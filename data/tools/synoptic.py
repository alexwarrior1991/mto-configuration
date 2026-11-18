#!/usr/bin/env python3
"""Lector del formato SINOPTICO: las vias en columnas emparejadas, o una por hoja.

Los once workbooks de los EPs ferroviarios traen una hoja 'HR Track' por via, con una
cabecera y las mismas ~36 columnas logicas. El sinoptico de la Linha Rubi (Metro do
Porto, RUBI.xlsx) no se parece en nada: es UNA tabla con las vias lado a lado, cada
campo en una columna por via. La via de una columna la dice su cabecera ('Maciços V1',
'P.K. (via 2)'), su posicion cuando la cabecera se repite sin decirla ('Vão (m)' dos
veces: la primera es de la primera via) o, en la hoja de la bretelle, una fila de
subcabecera con 'VIA 1' / 'VIA 2' debajo de cada cabecera. El workbook trae ademas cada via
general en una hoja propia ('VIA 1', 'VIA 2'), sin marca en las cabeceras y con la segunda
mensula en columnas sin cabecera: esas hojas MANDAN y el Sinóptico las completa
(merge_records). Las cabeceras estan en
portugues y en espanol, y hay dos que solo se distinguen por una tilde ('Tipologia' es
el sistema de catenaria; 'Tipología' es la funcion del apoyo).

Este modulo es lo que hace falta para leer esa hoja y no tiene ninguna dependencia de
los generadores: lo usan LOS DOS —build_lov_master.py cosecha con el los codigos y
build_profile_master.py los perfiles—, igual que workbook_common.py, para que el
catalogo y las referencias de los perfiles no puedan discrepar sobre lo que dice una
celda. Todo lo que este modulo decide viene de la seccion 'synoptic' de aliases.yml
y de 'format: synoptic' en topology.yml.

Reglas de la casa que se conservan:

- Las columnas se resuelven por NOMBRE de cabecera, nunca por indice. Las dos lecturas
  por posicion, obligadas, estan acotadas: una cabecera que se repite una vez por via sin
  decir de cual es (la n-esima es de la n-esima via declarada) y, en las hojas de via, la
  columna gemela sin cabecera pegada a la derecha de un campo de 'twins'.
- Nada se descarta en silencio: una cabecera desconocida va a NO_RECONOCIDO, las
  filas que no son un apoyo —hitos de estacion, notas al pie— salen con su motivo, y lo
  que no se lee esta declarado en aliases.yml ('ignored', 'ignored_patterns').
- El origen escribe en portugues y en espanol; a la base de datos llega INGLES. La
  tabla 'translations' es la unica que conoce las grafias del origen.
"""

from __future__ import annotations

import collections
import re
from decimal import Decimal, InvalidOperation

from openpyxl.utils import get_column_letter

from workbook_common import (
    MAX_COLS,
    MAX_ROWS_TRACK,
    code_tokens,
    is_blank,
    is_noise,
    norm_header,
    squash,
)

SYNOPTIC_FORMAT = "synoptic"

# La cabecera esta en las primeras filas (en la 1 en el sinoptico, debajo del titulo del
# caderno en la hoja de la bretelle). Se localiza por contenido, no por numero de fila.
HEADER_SEARCH_ROWS = 12

# Campos multivalor del sinoptico y la entidad LOV a la que va cada campo. 'EQUIPMENT'
# no es un campo del perfil: es la columna 'Equipamentos associados', que mezcla anclajes
# con aparatos de seccionamiento y se reparte por codigo con la tabla 'routing'.
MULTI_FIELDS = ("SECTIONING", "EQUIPMENT")
SINGLE_LOV_FIELDS = ("SUPPORT_TYPE", "POLE_TYPE", "FOUNDATION", "ASSEMBLY_CONFIGURATION")
CANTILEVER_FIELDS = ("CANTILEVER_TYPE", "STAGGER", "CW_HEIGHT")

FIELD_ENTITY = {
    "SECTIONING": "Sectioning",
    "ANCHORAGE": "Anchorage",
    "SECTIONING_FEEDING": "DisconnectorFunction",
    "SUPPORT_TYPE": "SupportType",
    "POLE_TYPE": "PoleType",
    "FOUNDATION": "Foundation",
    "ASSEMBLY_CONFIGURATION": "AssemblyConfiguration",
    "CANTILEVER_TYPE": "CantileverType",
}

# Un bloque es lo que la hoja dice de UNA via: sus columnas, repartidas por toda la tabla.
#   single  campo -> columnas (casi siempre una; 'SUPORTE V2' esta dos veces)
#   second  campo de mensula -> columnas de la SEGUNDA mensula ('Consolas 2 V1')
#   multi   campo multivalor -> todas sus columnas
#   unmapped  [(etiqueta, columna)] de las columnas reales sin campo en el dominio
#   columns   todas las anteriores: lo que decide si una fila trae algo de esta via
#   names     los nombres (sin marca de via) de sus columnas sin campo
Block = collections.namedtuple("Block", "marker single second multi unmapped labels columns names")
# shared: columnas sin campo que valen para todas las vias ('Activo', 'Terreno').
# bases: etiqueta de una columna sin campo -> su nombre sin la marca de via.
SynopticLayout = collections.namedtuple(
    "SynopticLayout", "sheet rows header_index first_data data_end blocks shared bases")


def is_synoptic(declared) -> bool:
    """True si el EP declara 'format: synoptic' en topology.yml."""
    return bool(declared) and squash(declared.get("format")).lower() == SYNOPTIC_FORMAT


def synoptic_config(cfg) -> dict:
    scfg = cfg.get("synoptic")
    if not scfg:
        raise KeyError("aliases.yml no tiene la seccion 'synoptic'")
    return scfg


def cell(row, index):
    return row[index] if index is not None and index < len(row) else None


def first_value(row, indexes):
    """El primer valor con dato de las columnas de un campo, o None."""
    for index in indexes or ():
        value = cell(row, index)
        if not is_blank(value):
            return value
    return None


# --------------------------------------------------------------------------------
# Plano de la hoja
# --------------------------------------------------------------------------------

def block_tags(scfg) -> list[tuple[str, str]]:
    """(marca, via) de todas las vias, la marca mas larga primero.

    Una marca es lo que la cabecera escribe para decir de que via es la columna:
    ' V1', '(via 1)'. Lo que queda al quitarla es el nombre de la columna.
    """
    tags = [(str(tag).lower(), squash(marker).upper())
            for marker, values in scfg["blocks"].items() for tag in values]
    return sorted(tags, key=lambda item: -len(item[0]))


def split_tag(name: str, tags) -> tuple[str, str | None]:
    """Cabecera normalizada -> (nombre sin la marca de via, via o None)."""
    for tag, marker in tags:
        if name.endswith(tag):
            return name[:len(name) - len(tag)].strip(), marker
    return name, None


def column_field(value) -> tuple[str, int]:
    """Entrada de 'columns' -> (campo, mensula). La mensula es 1 salvo que diga 'slot'."""
    if isinstance(value, dict):
        return value["field"], int(value.get("slot", 1))
    return value, 1


def synoptic_layout(ws, ep, cfg, report, only=None):
    """Plano de la hoja: filas, cabecera, las columnas de cada via y el fin de los datos.

    ``report`` es el acumulador del generador que llama (Master o Catalogue): los dos
    tienen ``unrecognised(kind, value, ep, sheet, row)``, que es lo unico que se usa.
    ``only`` es la via que topology.yml declara para la hoja: si la cabecera del apoyo no
    dice de que via es ('SUPORTE' a secas, sin subcabecera), la hoja ENTERA es de esa via
    (las hojas 'VIA 1' y 'VIA 2'), y las marcas que lleve alguna cabecera no reparten nada.
    Devuelve None si la hoja no tiene cabecera o a una via le falta el apoyo o el KP.
    """
    scfg = synoptic_config(cfg)
    sheet = ws.title
    rows = list(ws.iter_rows(min_row=1, max_row=min(ws.max_row, MAX_ROWS_TRACK),
                             max_col=MAX_COLS, values_only=True))
    tags = block_tags(scfg)
    markers = [squash(marker).upper() for marker in scfg["blocks"]]
    columns = {norm_header(k): column_field(v) for k, v in scfg["columns"].items()}
    unmapped = {norm_header(v) for v in scfg.get("unmapped", [])}
    shared = {norm_header(v) for v in scfg.get("shared", [])}
    ignored = {norm_header(v) for v in scfg.get("ignored", [])}
    patterns = [re.compile(p, re.IGNORECASE) for p in scfg.get("ignored_patterns", [])]
    twin_fields = set(scfg.get("twins", []))

    def is_header(row):
        return any(columns.get(split_tag(norm_header(value), tags)[0], ("",))[0] == "PROFILE_ID"
                   for value in row)

    header_index = next(
        (index for index, row in enumerate(rows[:HEADER_SEARCH_ROWS]) if is_header(row)), None)
    if header_index is None:
        report.unrecognised("hoja sinoptico sin cabecera", sheet, ep, sheet, 1)
        return None
    header = rows[header_index]
    header_number = header_index + 1
    width = max(len(row) for row in rows)

    # La hoja de la bretelle dice la via en una SUBCABECERA: la fila de debajo solo
    # trae 'VIA 1' / 'VIA 2', una por columna, y la cabecera de arriba vale para las dos.
    below = rows[header_index + 1] if header_index + 1 < len(rows) else ()
    below_texts = [squash(value).upper() for value in below]
    sub = below_texts if (any(below_texts) and all(t in markers for t in below_texts if t)) else None
    first_data = header_index + (2 if sub else 1)
    id_marks = {split_tag(norm_header(value), tags)[1] for value in header
                if columns.get(split_tag(norm_header(value), tags)[0], ("",))[0] == "PROFILE_ID"}
    whole = squash(only).upper() if only and not sub and id_marks == {None} else None

    # Cuantas veces esta cada cabecera que no dice su via: la que esta una vez por via
    # se reparte por posicion, la primera para la primera via declarada.
    untagged = collections.Counter()
    if not sub:
        for value in header:
            name = norm_header(value)
            if name and split_tag(name, tags)[1] is None:
                untagged[name] += 1
    position = collections.Counter()

    parts = {marker: {"single": collections.defaultdict(list),
                      "second": collections.defaultdict(list),
                      "multi": collections.defaultdict(list),
                      "unmapped": [], "labels": {}, "names": set()} for marker in markers}
    shared_columns, skipped, bases = [], set(), {}
    # (clase, etiqueta, via, nombre, campo, columna) de la ultima cabecera leida
    previous = None
    carried = ""                   # con subcabecera, la cabecera vale para sus columnas

    def keep(kind, label, marker, index, base):
        if kind == "ignored":
            skipped.add(index)
            return
        bases[label] = base
        if kind == "shared":
            shared_columns.append((label, index))
        else:
            parts[marker]["unmapped"].append((label, index))
            parts[marker]["names"].add(base)

    for index in range(width):
        raw = cell(header, index)
        name = norm_header(raw)
        if sub:
            if name:
                carried = raw
            marker = sub[index] if index < len(sub) else ""
            if not marker:
                if name:
                    report.unrecognised("cabecera sinoptico sin via", name, ep, sheet, header_number)
                skipped.add(index)
                continue
            raw, name = carried, split_tag(norm_header(carried), tags)[0]
        elif not name:
            # Una columna sin cabecera es la continuacion de la que tiene a la izquierda
            # (una celda de cabecera combinada) si esa no tiene campo o no se lee. Detras de
            # un campo solo se lee como ese campo si es su GEMELA: la columna pegada a la
            # derecha de un campo de 'twins', que en las hojas de via es la segunda mensula
            # (o el segundo equipo) del apoyo. Cualquier otra se conserva con su letra.
            letter = get_column_letter(index + 1)
            if previous is not None and previous[0] == "field" and previous[4] in twin_fields \
                    and previous[5] == index - 1:
                part, field = parts[previous[2]], previous[4]
                part["labels"][index] = f"{previous[1]} ({letter})"
                part["multi" if field in MULTI_FIELDS else "second"][field].append(index)
                previous = None
            elif previous is None or previous[0] == "field":
                keep("shared", f"sin cabecera {letter}", None, index, f"sin cabecera {letter}")
            else:
                keep(previous[0], f"{previous[1]} ({letter})", previous[2], index, previous[3])
            continue
        else:
            full = name
            name, marker = split_tag(full, tags)
            if any(p.search(full) for p in patterns) or full in ignored or name in ignored \
                    or (marker and not name):
                # Ignorada por declaracion, o un marcador de via suelto ('VIA 1'): relleno.
                skipped.add(index)
                previous = ("ignored", squash(raw), None, name, None, index)
                continue
            if whole:
                marker = whole
                if name not in columns and name not in unmapped and full in shared:
                    keep("unmapped", squash(raw), marker, index, full)
                    previous = ("unmapped", squash(raw), marker, full, None, index)
                    continue
            elif marker is None:
                if full in shared:
                    label = squash(raw)
                    if untagged[full] > 1:
                        label = f"{label} ({get_column_letter(index + 1)})"
                    keep("shared", label, None, index, full)
                    previous = ("shared", squash(raw), None, full, None, index)
                    continue
                if (full in columns or full in unmapped) and untagged[full] == len(markers):
                    marker = markers[position[full]]
                    position[full] += 1
                else:
                    kind = "cabecera sinoptico"
                    if full in columns or full in unmapped:
                        kind = "cabecera sinoptico que no dice su via"
                    report.unrecognised(kind, full, ep, sheet, header_number)
                    skipped.add(index)
                    previous = ("ignored", squash(raw), None, name, None, index)
                    continue

        label = squash(raw)
        part = parts[marker]
        part["labels"][index] = label
        if name in columns:
            field, slot = columns[name]
            if field in MULTI_FIELDS:
                part["multi"][field].append(index)
            elif slot == 2:
                part["second"][field].append(index)
            else:
                part["single"][field].append(index)
            previous = ("field", label, marker, name, field if slot == 1 else None, index)
        elif name in unmapped:
            keep("unmapped", label, marker, index, name)
            previous = ("unmapped", label, marker, name, None, index)
        else:
            report.unrecognised("cabecera sinoptico", name, ep, sheet, header_number)
            skipped.add(index)
            previous = ("ignored", label, None, name, None, index)

    blocks = {}
    for marker, part in parts.items():
        own = {index for group in ("single", "second", "multi") for indexes in part[group].values()
               for index in indexes} | {index for _, index in part["unmapped"]}
        if not own:
            continue                           # la hoja no trae esa via
        for required in ("PROFILE_ID", "KP"):
            if required not in part["single"]:
                report.unrecognised(f"bloque sin columna {required}", marker, ep, sheet,
                                    header_number)
                return None
        blocks[marker] = Block(marker, dict(part["single"]), dict(part["second"]),
                               dict(part["multi"]), part["unmapped"], part["labels"], own,
                               part["names"])
    if not blocks:
        report.unrecognised("hoja sinoptico sin columnas de via", sheet, ep, sheet, header_number)
        return None

    aside = {index for _, index in shared_columns}
    data_end = find_data_end(rows, first_data, blocks.values(),
                             [index for index in range(width) if index not in aside])
    return SynopticLayout(sheet, rows, header_index, first_data, data_end, blocks,
                          shared_columns, bases)


def block_is_blank(row, block: Block) -> bool:
    """True si la fila no trae nada de esta via."""
    return all(is_blank(cell(row, index)) for index in block.columns)


def find_data_end(rows, first_data, blocks, columns) -> int:
    """Indice de la primera fila en blanco despues del primer apoyo.

    Debajo de los datos el sinoptico lleva notas al pie, que no son apoyos aunque
    escriban un codigo. Lo que las separa es una fila en blanco, y en blanco quiere decir
    en TODAS las columnas de via, tambien las que no se leen: los ultimos apoyos de la
    zona 3713 no traen mas que su codigo, y su fila intermedia solo el relleno 'VIA 1' /
    'VIA 2'. Las columnas que no son de ninguna via ('shared') no cuentan: una tabla de
    medicion suelta ocupa justo las filas que separan los apoyos de las notas. Lo que
    venga despues sale en DESCARTADOS con su fila, para que no se pierda en silencio.
    """
    seen_profile = False
    for offset in range(first_data, len(rows)):
        row = rows[offset]
        if all(is_blank(cell(row, index)) for index in columns):
            if seen_profile:
                return offset
            continue
        seen_profile = seen_profile or any(
            not is_blank(first_value(row, block.single["PROFILE_ID"])) for block in blocks)
    return len(rows)


# --------------------------------------------------------------------------------
# Filas de un bloque
# --------------------------------------------------------------------------------

def parse_kp(value):
    """KP de una celda del sinoptico, en metros, o None si no es un KP.

    Ademas del numero admite la kilometracion con signo mas —'1+118,8' son 1118,8 m—
    y la coma decimal. '??' y '-' son huecos. El signo de delante es el del KP entero, no
    el de los kilometros: '-0+010' son -10 m y '-1+200', -1200 m (antes del origen).
    """
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return Decimal(repr(value)) if isinstance(value, float) else Decimal(value)
    text = squash(value).replace(" ", "")
    if not text:
        return None
    match = re.fullmatch(r"([-+]?)(\d+)\+(\d+(?:[.,]\d+)?)", text)
    if match:
        sign, kilometres, metres = match.groups()
        distance = Decimal(kilometres) * 1000 + Decimal(metres.replace(",", "."))
        return -distance if sign == "-" else distance
    try:
        return Decimal(text.replace(",", "."))
    except InvalidOperation:
        return None


def is_landmark(code: str, cfg) -> bool:
    """True si el texto de la columna del apoyo es un hito del trazado.

    'Iníc. Est. Campo Alegre (00+966)', 'Fim Ponte (02+059)', 'Cruzamento Rodoviário',
    'Existente': no traen KP, y el texto —declarado en 'synoptic.landmarks'— confirma
    que lo son.
    """
    pattern = synoptic_config(cfg).get("landmarks")
    return bool(pattern) and re.match(pattern, squash(code), re.IGNORECASE) is not None


def synoptic_records(layout: SynopticLayout, marker, cfg):
    """Las filas de una via, agrupadas por apoyo.

    Devuelve una lista de dicts con ``kind`` en {'profile', 'landmark', 'footnote',
    'orphan'}; solo los 'profile' llevan datos. Las filas intermedias —las que no traen apoyo— pertenecen al apoyo
    ANTERIOR, igual que el vano en las hojas HR Track: es el vano hasta el apoyo
    siguiente, y el 'Seccionamento' o el 'Disp. Transic.' escritos entre dos apoyos son
    del primero. Una fila con KP y sin apoyo ('orphan') no es intermedia: es un apoyo sin
    identificar, y sale con su motivo en lugar de sumarse al anterior.
    """
    block = layout.blocks[squash(marker).upper()]
    single, second = block.single, block.second
    records, current = [], None

    for offset in range(layout.first_data, len(layout.rows)):
        row = layout.rows[offset]
        number = offset + 1                       # el numero que ensena Excel
        raw_id = first_value(row, single["PROFILE_ID"])
        code = squash(raw_id)

        if offset >= layout.data_end:
            if not block_is_blank(row, block):
                records.append({"kind": "footnote", "number": number, "code": code,
                                "detail": code or first_text(row, block)})
            continue

        kp_raw = first_value(row, single["KP"])
        kp = parse_kp(kp_raw)
        if is_blank(raw_id) or is_noise(code):
            if kp is not None:
                records.append({"kind": "orphan", "number": number, "code": code,
                                "detail": f"KP {squash(kp_raw)}"})
            elif current is not None:
                attach(current, row, number, block, layout)
            continue

        if kp is None and is_landmark(code, cfg):
            # Un hito se escribe en la fila intermedia, donde iria el hueco del apoyo: lo
            # demas de esa fila (el vano, un 'PA30') sigue siendo del apoyo anterior.
            records.append({"kind": "landmark", "number": number, "code": code})
            if current is not None:
                attach(current, row, number, block, layout)
            continue
        current = {
            "kind": "profile", "number": number, "code": code,
            "kp_raw": kp_raw, "kp": kp,
            "span": first_value(row, single.get("SPAN")), "span_between": False,
            "single": {field: first_value(row, single.get(field)) for field in SINGLE_LOV_FIELDS},
            "rail_pole_distance": first_value(row, single.get("RAIL_POLE_DISTANCE")),
            "multi": {field: [] for field in MULTI_FIELDS},
            "cantilever": [
                {field: first_value(row, single.get(field)) for field in CANTILEVER_FIELDS},
                {field: first_value(row, second.get(field)) for field in CANTILEVER_FIELDS},
            ],
            "unmapped": [],
        }
        collect_multi(current, row, number, block)
        collect_unmapped(current, row, number, block, layout)
        records.append(current)

    return records


# --------------------------------------------------------------------------------
# Una via de dos hojas: la suya, que manda, y la que la completa
# --------------------------------------------------------------------------------

KP_TOLERANCE = Decimal("0.005")


def plain(value):
    """Valor de una celda para compararlo con el de otra hoja: sin huecos ni decimales de coma flotante."""
    if is_blank(value):
        return None
    text = squash(value)
    try:
        return format(round(Decimal(text.replace(",", ".")), 3).normalize(), "f")
    except InvalidOperation:
        return text


def same_kp(one, other) -> bool:
    if one is None or other is None:
        return one is None and other is None
    return abs(one - other) < KP_TOLERANCE


def pair_records(primary, secondary):
    """Casa los apoyos de la hoja que manda con los de la que la completa.

    Devuelve {posicion en primary: (posicion en secondary, como)}. Por orden de fiabilidad:
    mismo nombre y mismo KP; despues el mismo KP con otro nombre, si en ese KP solo queda
    un apoyo sin casar en cada hoja (la zona 3713 esta renumerada en una de las dos); y
    al final el mismo nombre con otro KP, si el nombre no se repite. Casar primero por
    nombre a secas emparejaria, en la zona renumerada, apoyos distintos.
    """
    pairs, taken = {}, set()

    def free(records, used):
        return [i for i, r in enumerate(records) if r["kind"] == "profile" and i not in used]

    for i in free(primary, pairs):
        for j in free(secondary, taken):
            if primary[i]["code"] == secondary[j]["code"] and same_kp(primary[i]["kp"], secondary[j]["kp"]):
                pairs[i] = (j, "id")
                taken.add(j)
                break
    for how, match in (("kp", lambda a, b: a["kp"] is not None and same_kp(a["kp"], b["kp"])),
                       ("id-otro-kp", lambda a, b: a["code"] == b["code"])):
        left, right = free(primary, pairs), free(secondary, taken)
        for i in left:
            mine = [j for j in right if j not in taken and match(primary[i], secondary[j])]
            theirs = [k for k in left if k not in pairs and match(primary[k], secondary[mine[0]])] \
                if len(mine) == 1 else []
            if len(mine) == 1 and theirs == [i]:
                pairs[i] = (mine[0], how)
                taken.add(mine[0])
    return pairs


def merge_records(primary, block: Block, sheet, secondary, other: SynopticLayout, other_sheet):
    """Los apoyos de una via que tiene dos hojas: la suya, que manda, y otra que la completa.

    ``primary`` son los registros de la hoja que manda y ``block`` sus columnas; de
    ``secondary`` solo entra LO QUE LA PRIMERA NO TRAE: los campos y las columnas sin campo
    que su hoja no tiene (la configuracion de montaje, 'Activo', 'Terreno') y los apoyos
    que no estan en ella. Una celda de una columna que si tiene manda como este, vacia
    incluida. Nada se pierde en silencio: cada apoyo lleva en ``notes`` como se caso y lo
    que la otra hoja dice distinto, y lo que no se caso entra o sale con su motivo.
    """
    pairs = pair_records(primary, secondary)
    matched = {j: i for i, (j, _) in pairs.items()}
    merged, after = [], collections.defaultdict(list)

    # Un apoyo que solo esta en la segunda hoja entra detras del ultimo apoyo casado que
    # lo precede alli (o al principio).
    anchor = None
    for j, item in enumerate(secondary):
        if j in matched:
            anchor = matched[j]
        elif item["kind"] == "profile":
            after[anchor].append(dict(item, sheet=other_sheet, notes=[
                ("apoyo que solo esta en %s: entra de alli" % other.sheet, item["code"])]))
        elif item["kind"] in ("footnote", "orphan"):
            after[anchor].append(dict(item, sheet=other_sheet))
        # Los hitos de la segunda hoja son los mismos de la primera: no se repiten.

    merged.extend(after[None])
    for i, item in enumerate(primary):
        if item["kind"] != "profile":
            merged.append(dict(item, sheet=sheet))
            merged.extend(after[i])
            continue
        record = dict(item, sheet=sheet, notes=[])
        if i not in pairs:
            record["notes"].append(("apoyo que no esta en %s: sin lo que solo trae esa hoja"
                                    % other.sheet, item["code"]))
            merged.append(record)
            continue
        extra = secondary[pairs[i][0]]
        where = "%s fila %d" % (other.sheet, extra["number"])
        if pairs[i][1] != "id":
            record["notes"].append((
                "apoyo casado con %s por %s" % (other.sheet, {
                    "kp": "su KP: alli tiene otro nombre",
                    "id-otro-kp": "su nombre: alli tiene otro KP"}[pairs[i][1]]),
                "%s = %s (%s), KP %s / %s" % (item["code"], extra["code"], where,
                                               plain(item["kp_raw"]), plain(extra["kp_raw"]))))

        differences = []

        def take(field, mine, theirs, present):
            """El valor de la hoja que manda si tiene esa columna; si no, el de la otra."""
            if not present:
                return theirs
            if plain(mine) != plain(theirs) and field is not None:
                differences.append("%s: %s / %s" % (field, plain(mine), plain(theirs)))
            return mine

        take("KP", item["kp_raw"], extra["kp_raw"], True)
        record["span"] = take("SPAN", item["span"], extra["span"], "SPAN" in block.single)
        record["rail_pole_distance"] = take(
            "RAIL_POLE_DISTANCE", item["rail_pole_distance"], extra["rail_pole_distance"],
            "RAIL_POLE_DISTANCE" in block.single)
        record["single"] = {field: take(field, item["single"][field], extra["single"][field],
                                        field in block.single) for field in SINGLE_LOV_FIELDS}
        record["cantilever"] = [
            {field: take("%s %d" % (field, slot + 1), item["cantilever"][slot][field],
                         extra["cantilever"][slot][field], field in columns)
             for field in CANTILEVER_FIELDS}
            for slot, columns in enumerate((block.single, block.second))]
        record["multi"] = {}
        for field in MULTI_FIELDS:
            if field not in block.multi:
                record["multi"][field] = extra["multi"][field]
                continue
            record["multi"][field] = item["multi"][field]
            mine = sorted({plain(v[0]) for v in item["multi"][field]} - {None})
            theirs = sorted({plain(v[0]) for v in extra["multi"][field]} - {None})
            if mine != theirs:
                differences.append("%s: %s / %s" % (field, " + ".join(mine) or None,
                                                    " + ".join(theirs) or None))
        # Las columnas sin campo de la otra hoja que esta no tiene.
        record["unmapped"] = list(item["unmapped"]) + [
            entry for entry in extra["unmapped"] if other.bases.get(entry[0]) not in block.names]
        for difference in differences:
            record["notes"].append(("manda la hoja de la via: %s dice otra cosa" % other.sheet,
                                    "%s %s (%s)" % (item["code"], difference, where)))
        merged.append(record)
        merged.extend(after[i])
    return merged


def declared_tracks(worksheets, declared, ep, cfg, report):
    """Las vias que topology.yml declara para un workbook sinoptico, con sus apoyos.

    Devuelve una lista de (declaracion, hoja de origen, registros). Es lo que usan LOS DOS
    generadores, para que el catalogo y los perfiles salgan de la misma lectura. Una via
    puede declarar 'complement: {sheet, block}': su hoja manda y esa otra la completa
    (merge_records). Una declaracion con 'skip' no se lee: devuelve registros None para que
    quien llama lo anote. Una hoja del workbook que ninguna declaracion nombra, o una
    declarada que no esta, va a NO_RECONOCIDO.
    """
    by_title = {squash(ws.title): ws for ws in worksheets}
    tracks = list(declared.get("tracks") or [])
    named = set()
    for decl in tracks:
        named.add(squash(decl.get("sheet")))
        if decl.get("complement"):
            named.add(squash(decl["complement"].get("sheet")))
    for title in by_title:
        if title not in named:
            # Como una hoja HR Track: lo que no se lee se declara con 'skip' y su motivo.
            report.unrecognised("hoja sinoptico sin declarar", title, ep, title)
    for title in sorted(named - set(by_title)):
        report.unrecognised("hoja declarada que no existe en el workbook", title, ep, title)

    # Cuantas vias declara cada hoja: la que declara una sola puede ser toda de esa via.
    uses = collections.Counter(squash(d.get("sheet")) for d in tracks if not d.get("skip"))
    layouts = {}

    def layout_of(title, only):
        key = (title, only)
        if key not in layouts:
            layouts[key] = synoptic_layout(by_title[title], ep, cfg, report, only=only) \
                if title in by_title else None
        return layouts[key]

    def block_of(title, marker, only=None):
        layout = layout_of(title, only)
        if layout is None:
            return None, None
        if marker not in layout.blocks:
            report.unrecognised("bloque de via sin marcador en la cabecera", marker, ep,
                                layout.sheet, layout.header_index + 1)
            return layout, None
        return layout, layout.blocks[marker]

    found = []
    for decl in tracks:
        title = squash(decl.get("sheet"))
        if decl.get("skip"):
            if title in by_title:
                found.append((decl, by_title[title].title, None))
            continue
        marker = squash(decl.get("block")).upper()
        layout, block = block_of(title, marker, marker if uses[title] == 1 else None)
        if block is None:
            continue
        sheet = f"{layout.sheet} / {marker}"
        records = [dict(item, sheet=sheet) for item in synoptic_records(layout, marker, cfg)]
        complement = decl.get("complement")
        if complement:
            other_marker = squash(complement.get("block") or marker).upper()
            other, other_block = block_of(squash(complement.get("sheet")), other_marker)
            if other_block is not None:
                records = merge_records(records, block, sheet,
                                        synoptic_records(other, other_marker, cfg), other,
                                        f"{other.sheet} / {other_marker}")
        found.append((decl, sheet, records))
    return found


def attach(current, row, number, block: Block, layout: SynopticLayout):
    """Lo que trae una fila intermedia se apunta al apoyo anterior.

    El vano se escribe en la fila intermedia, y ese manda: el que algun apoyo trae ademas
    en su propia fila solo vale si entre el y el siguiente no hay ninguno.
    """
    span = first_value(row, block.single.get("SPAN"))
    if not is_blank(span) and not current["span_between"]:
        current["span"], current["span_between"] = span, True
    collect_multi(current, row, number, block)
    collect_unmapped(current, row, number, block, layout)


def collect_multi(current, row, number, block: Block):
    for field in MULTI_FIELDS:
        for index in block.multi.get(field, []):
            value = cell(row, index)
            if not is_blank(value):
                current["multi"][field].append((squash(value), number, block.labels[index]))


def collect_unmapped(current, row, number, block: Block, layout: SynopticLayout):
    """Las columnas sin campo, las de la via y las que valen para todas.

    Un valor que se repite fila a fila ('Activo', 'Terreno': estan tambien en la fila
    intermedia) se apunta una vez por apoyo.
    """
    seen = {(label, value) for label, value, _ in current["unmapped"]}
    for label, index in list(block.unmapped) + list(layout.shared):
        text = squash(cell(row, index))
        if text and not is_blank(text) and (label, text) not in seen:
            seen.add((label, text))
            current["unmapped"].append((label, text, number))


def first_text(row, block: Block) -> str:
    for index in sorted(block.columns):
        value = cell(row, index)
        if not is_blank(value):
            return squash(value)
    return ""


# --------------------------------------------------------------------------------
# Traduccion al catalogo
# --------------------------------------------------------------------------------

def translate(field, text, cfg):
    """Codigo del catalogo para una grafia del origen, o None si no esta declarada.

    Devuelve '' cuando la tabla dice que esa grafia NO es un codigo ('S' es la
    suspension simple, sin seccionamiento), que no es lo mismo que no estar declarada.
    """
    table = (synoptic_config(cfg).get("translations") or {}).get(field) or {}
    upper = squash(text).upper()
    for raw, target in table.items():
        if squash(raw).upper() == upper:
            return "" if target is None else squash(target)
    return None


def is_hole(text: str) -> bool:
    """True si la celda no lleva codigo: vacia, '0', '-' o un error de formula.

    No es is_noise: en las hojas HR Track 'M1', 'M2' y 'M3' son subcabeceras de las
    columnas de mensula, pero en el sinoptico son maciços de verdad.
    """
    return is_blank(text) or squash(text).startswith("#")


def translate_single(field, raw, cfg) -> str:
    """Un campo de un solo codigo: hueco si esta vacio o es ruido, si no traducido."""
    text = squash(raw)
    if is_hole(text):
        return ""
    translated = translate(field, text, cfg)
    return text if translated is None else translated


Translated = collections.namedtuple("Translated", "codes rejected noise unrouted")


def translate_codes(field, cells, cfg) -> Translated:
    """Reparte las celdas de un campo multivalor en codigos del catalogo.

    ``cells`` son tuplas (valor, fila) o (valor, fila, cabecera de origen). Devuelve los
    codigos como pares (campo_destino, codigo) sin repetidos, mas lo que no ha entrado:
    ``rejected`` (erratas declaradas, como 'Anc'), ``noise`` (anotaciones como
    'Canton 396,4 m', que se conservan en NO_MAPEADO con su cabecera) y ``unrouted``
    (codigos de 'Equipamentos' que ninguna regla de 'routing' reparte).

    Primero se prueba la celda ENTERA en la tabla de traduccion —'Transição Catenaria'
    y 'Caix. Secc. e Con.' llevan espacios— y solo si no esta se parte con code_tokens,
    la misma particion que usan las hojas HR Track, y se traduce trozo a trozo.
    """
    scfg = synoptic_config(cfg)
    rejections = {squash(v).upper() for v in (scfg.get("rejections") or {}).get(field, [])}
    noise = (scfg.get("noise") or {}).get(field) or {}
    reassignment = (scfg.get("reassignment") or {}).get(field) or {}
    routing = scfg.get("routing") or {}

    codes, rejected, noisy, unrouted = [], [], [], []
    for raw, number, *label in cells:
        text = squash(raw)
        if is_hole(text):
            continue
        pattern = noise.get("regex")
        if pattern and re.fullmatch(pattern, text, re.IGNORECASE):
            noisy.append((text, number, label[0] if label else field))
            continue

        whole = translate(field, text, cfg)
        if whole is not None:
            pieces = [whole]
        else:
            pieces = []
            for token in code_tokens(text):
                translated = translate(field, token, cfg)
                pieces.append(token if translated is None else translated)

        for piece in pieces:
            piece = squash(piece)
            if not piece:
                continue
            if piece.upper() in rejections:
                rejected.append((piece, number))
                continue
            target, code = field, piece
            moved = lookup(reassignment, piece)
            if moved is not None:
                target, code = moved["to"], squash(moved["code"])
            elif field == "EQUIPMENT":
                target = next((destination for destination, accepted in routing.items()
                               if any(squash(v).upper() == piece.upper() for v in accepted)),
                              None)
                if target is None:
                    unrouted.append((piece, number))
                    continue
            if not any(t == target and c.upper() == code.upper() for t, c in codes):
                codes.append((target, code))

    return Translated(codes, rejected, noisy, unrouted)


def lookup(table: dict, code: str):
    upper = squash(code).upper()
    for raw, value in table.items():
        if squash(raw).upper() == upper:
            return value
    return None


def describe(entity, code, cfg) -> str:
    """Descripcion inglesa declarada para un codigo nuevo, o '' si no hay ninguna.

    El sinoptico no trae leyenda, asi que las descripciones se declaran en aliases.yml:
    una a una, o por patron para las familias numeradas ('C.F.21').
    """
    table = (synoptic_config(cfg).get("descriptions") or {}).get(entity) or {}
    for raw, value in table.items():
        if raw != "pattern" and squash(raw).upper() == squash(code).upper():
            return squash(value)
    for regex, template in (table.get("pattern") or {}).items():
        match = re.fullmatch(regex, squash(code))
        if match:
            return match.expand(template)
    return ""


def lov_columns(cfg) -> list[tuple[str, str]]:
    """(cabecera del origen, entidad LOV) para la hoja USO_TRACKS del catalogo."""
    scfg = synoptic_config(cfg)
    found = []
    for header, value in scfg["columns"].items():
        field, _ = column_field(value)
        if field == "EQUIPMENT":
            found.append((f"sinoptico: {header}", "Anchorage / DisconnectorFunction"))
        elif field in FIELD_ENTITY:
            found.append((f"sinoptico: {header}", FIELD_ENTITY[field]))
    return found
