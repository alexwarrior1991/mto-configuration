#!/usr/bin/env python3
"""Lector del formato SINOPTICO: una tabla con las vias en columnas emparejadas.

Los once workbooks de los EPs ferroviarios traen una hoja 'HR Track' por via, con una
cabecera y las mismas ~36 columnas logicas. El sinoptico de la Linha Rubi (Metro do
Porto, RUBI.xlsx) no se parece en nada: es UNA tabla con las vias lado a lado, cada
campo en una columna por via. La via de una columna la dice su cabecera ('Maciços V1',
'P.K. (via 2)'), su posicion cuando la cabecera se repite sin decirla ('Vão (m)' dos
veces: la primera es de la primera via) o, en la hoja de la bretelle, una fila de
subcabecera con 'VIA 1' / 'VIA 2' debajo de cada cabecera. Las cabeceras estan en
portugues y en espanol, y hay dos que solo se distinguen por una tilde ('Tipologia' es
el sistema de catenaria; 'Tipología' es la funcion del apoyo).

Este modulo es lo que hace falta para leer esa hoja y no tiene ninguna dependencia de
los generadores: lo usan LOS DOS —build_lov_master.py cosecha con el los codigos y
build_profile_master.py los perfiles—, igual que workbook_common.py, para que el
catalogo y las referencias de los perfiles no puedan discrepar sobre lo que dice una
celda. Todo lo que este modulo decide viene de la seccion 'synoptic' de aliases.yml
y de 'format: synoptic' en topology.yml.

Reglas de la casa que se conservan:

- Las columnas se resuelven por NOMBRE de cabecera, nunca por indice. La unica lectura
  por posicion, obligada, es la de una cabecera que se repite una vez por via sin decir
  de cual es: la n-esima es de la n-esima via declarada.
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
Block = collections.namedtuple("Block", "marker single second multi unmapped labels columns")
# shared: columnas sin campo que valen para todas las vias ('Activo', 'Terreno').
SynopticLayout = collections.namedtuple(
    "SynopticLayout", "sheet rows header_index first_data data_end blocks shared")


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


def synoptic_layout(ws, ep, cfg, report):
    """Plano de la hoja: filas, cabecera, las columnas de cada via y el fin de los datos.

    ``report`` es el acumulador del generador que llama (Master o Catalogue): los dos
    tienen ``unrecognised(kind, value, ep, sheet, row)``, que es lo unico que se usa.
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
                      "unmapped": [], "labels": {}} for marker in markers}
    shared_columns, skipped = [], set()
    previous = None                # (clase, etiqueta, via) de la ultima cabecera leida
    carried = ""                   # con subcabecera, la cabecera vale para sus columnas

    def keep(kind, label, marker, index):
        if kind == "ignored":
            skipped.add(index)
        elif kind == "shared":
            shared_columns.append((label, index))
        else:
            parts[marker]["unmapped"].append((label, index))

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
            # (una celda de cabecera combinada) si esa no tiene campo o no se lee. Nunca se
            # lee como un campo: detras de uno, lo que traiga se conserva con su letra.
            letter = get_column_letter(index + 1)
            if previous is None or previous[0] == "field":
                shared_columns.append((f"sin cabecera {letter}", index))
            else:
                keep(previous[0], f"{previous[1]} ({letter})", previous[2], index)
            continue
        else:
            full = name
            name, marker = split_tag(full, tags)
            if any(p.search(full) for p in patterns) or full in ignored or name in ignored \
                    or (marker and not name):
                # Ignorada por declaracion, o un marcador de via suelto ('VIA 1'): relleno.
                skipped.add(index)
                previous = ("ignored", squash(raw), None)
                continue
            if marker is None:
                if full in shared:
                    label = squash(raw)
                    if untagged[full] > 1:
                        label = f"{label} ({get_column_letter(index + 1)})"
                    shared_columns.append((label, index))
                    previous = ("shared", squash(raw), None)
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
                    previous = ("ignored", squash(raw), None)
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
            previous = ("field", label, marker)
        elif name in unmapped:
            part["unmapped"].append((label, index))
            previous = ("unmapped", label, marker)
        else:
            report.unrecognised("cabecera sinoptico", name, ep, sheet, header_number)
            skipped.add(index)
            previous = ("ignored", label, None)

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
                               dict(part["multi"]), part["unmapped"], part["labels"], own)
    if not blocks:
        report.unrecognised("hoja sinoptico sin columnas de via", sheet, ep, sheet, header_number)
        return None

    aside = {index for _, index in shared_columns}
    data_end = find_data_end(rows, first_data, blocks.values(),
                             [index for index in range(width) if index not in aside])
    return SynopticLayout(sheet, rows, header_index, first_data, data_end, blocks,
                          shared_columns)


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
    'orphan'}. Las filas intermedias —las que no traen apoyo— pertenecen al apoyo
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

        kind = "landmark" if kp is None and is_landmark(code, cfg) else "profile"
        current = {
            "kind": kind, "number": number, "code": code,
            "kp_raw": kp_raw, "kp": kp,
            "span": first_value(row, single.get("SPAN")),
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


def attach(current, row, number, block: Block, layout: SynopticLayout):
    """Lo que trae una fila intermedia se apunta al apoyo anterior."""
    if is_blank(current.get("span")):
        current["span"] = first_value(row, block.single.get("SPAN"))
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
