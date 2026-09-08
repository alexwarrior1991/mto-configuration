#!/usr/bin/env python3
"""Construye data/profile-master.xlsx a partir de los workbooks de Execution Package.

Por que este script vive fuera de la aplicacion
-----------------------------------------------
El mismo motivo que documenta build_lov_master.py, con dos vueltas de tuerca mas.
Las 177 hojas de trazado escriben las mismas ~36 columnas logicas de 66 formas
distintas, y una de ellas (EP6 / HR Track 1 HER) no tiene columna 'Sectionning', de
modo que todo lo posterior queda desplazado una posicion. Por eso las columnas se
resuelven SIEMPRE por nombre: leer por indice corromperia esa hoja en silencio.

Ademas hay conocimiento que no esta en los ficheros y que solo puede declarar una
persona: que vía es cada hoja, si esa vía cuelga de una estacion o del paquete, y
donde se parte una hoja que en realidad lleva dos tramos concatenados (EP9A trae
dos, con el KP reiniciado y 47 codigos de perfil repetidos). Eso vive en
topology.yml, versionado y revisable en el PR, no adivinado aqui.

Regla de oro: nunca descartar en silencio
-----------------------------------------
Una hoja sin declarar, una cabecera desconocida o un valor que no cabe en su columna
no se tiran: van a NO_RECONOCIDO o a DESCARTADOS con su motivo, y en el primer caso
el proceso termina con codigo != 0.

Uso
---
    python3 data/tools/build_profile_master.py --seed-topology   # solo la primera vez
    python3 data/tools/build_profile_master.py [carpeta] [-o salida.xlsx]
"""

from __future__ import annotations

import argparse
import collections
import os
import sys
from decimal import Decimal, InvalidOperation

import openpyxl
import yaml
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from workbook_common import (  # noqa: E402  (necesita el sys.path de arriba)
    MAX_ROWS_TRACK,
    TRACK_DATA_MAX_COL,
    discover,
    find_header_row,
    is_blank,
    is_noise,
    is_track_sheet,
    norm_header,
    squash,
)

CANTILEVER_SLOTS = 3

# Precision de cada columna numerica, tomada del esquema. Aceptar aqui mas de lo que
# admite la columna solo cambia el 400 con el campo señalado por un 500 del driver.
#   campo: (enteros, decimales, minimo o None)
NUMERIC_LIMITS = {
    "KP": (9, 3, Decimal(0)),
    "SPAN": (3, 3, Decimal(0)),
    "HEIGHT_CANTILEVER_SUPPORT": (6, 0, Decimal(0)),
    "POLE_GAUGE_LOCATION": (6, 0, Decimal(0)),
    "RAIL_POLE_DISTANCE": (6, 0, None),          # con signo: marca el lado de la via
    "STAGGER": (3, 0, None),
    "CATENARY_HEIGHT": (1, 3, None),
    "CW_ELEVATION": (1, 3, None),
    "CW_HEIGHT": (1, 3, None),
    "WIND_DEFLECTION": (1, 3, None),
    "ARM_ANGLE": (2, 3, None),
}
ARM_ANGLE_MIN, ARM_ANGLE_MAX = Decimal("-90"), Decimal("90")
STEADY_ARM_LENGTH_MIN, STEADY_ARM_LENGTH_MAX = 1, 2000

PROFILE_LOV_FIELDS = ("SECTIONING", "ANCHORAGE", "ANCHORAGE_FOUNDATION", "FOUNDATION",
                      "POLE_TYPE", "PORTAL", "RETURN_SUPPORT", "SECTIONING_FEEDING")

# A que catalogo pertenece cada columna de codigo. Es lo que permite canonicalizar y,
# sobre todo, comprobar que el codigo EXISTE habilitado antes de escribir el maestro:
# MasterDataService resuelve un codigo desconocido a null SIN QUEJARSE, asi que sin esta
# comprobacion el perfil se cargaria con la clave ajena vacia y el informe diria que todo
# fue bien. Es el mismo fallo silencioso que dejo profile_status vacia.
LOV_ENTITY = {
    "SECTIONING": "Sectioning",
    "ANCHORAGE": "Anchorage",
    "ANCHORAGE_FOUNDATION": "AnchorageFoundation",
    "FOUNDATION": "Foundation",
    "POLE_TYPE": "PoleType",
    "PORTAL": "Portal",
    "RETURN_SUPPORT": "ReturnSupport",
    "SECTIONING_FEEDING": "DisconnectorFunction",
    "CANTILEVER_TYPE": "CantileverType",
    "STEADY_ARM_TYPE": "SteadyArmType",
}

HEADER_FILL = PatternFill("solid", fgColor="1F3864")
REVIEW_FILL = PatternFill("solid", fgColor="FFF2CC")
HEADER_FONT = Font(color="FFFFFF", bold=True)


# --------------------------------------------------------------------------------
# Acumulador
# --------------------------------------------------------------------------------

class Master:
    """Junta las filas de las cinco hojas de datos y los tres informes."""

    def __init__(self):
        self.eps = []
        self.stations = []
        self.tracks = []
        self.profiles = []
        self.cantilevers = []
        self.unmapped = []
        self.discarded = []
        self.unknown = {}

    def discard(self, *, motivo, ep, hoja, fila, detalle=""):
        self.discarded.append({"motivo": motivo, "ep": ep, "hoja": hoja,
                               "fila": fila, "detalle": detalle})

    def unrecognised(self, kind, value, ep, sheet, row_index=None):
        key = (kind, value, ep, sheet)
        entry = self.unknown.get(key)
        if entry is None:
            self.unknown[key] = {"tipo": kind, "valor": value, "ep": ep,
                                 "hoja": sheet, "primera_fila": row_index,
                                 "apariciones": 0}
            entry = self.unknown[key]
        entry["apariciones"] += 1


# --------------------------------------------------------------------------------
# Normalizacion de valores
# --------------------------------------------------------------------------------

def to_decimal(value):
    """Decimal de una celda, o None si no es un numero.

    openpyxl devuelve float para las celdas numericas, y un float trae ruido: los
    milimetros del origen llegan como 1475.0000000000002. Se pasa por str para
    quedarse con la representacion corta, que es la que el humano ve en Excel.
    """
    if value is None:
        return None
    if isinstance(value, bool):
        return None
    text = squash(value).replace(",", ".")
    if not text:
        return None
    try:
        return Decimal(repr(value) if isinstance(value, float) else text)
    except (InvalidOperation, ValueError):
        return None


def fit_numeric(field, value, *, ep, sheet, row, master: Master):
    """Redondea a la escala de la columna y comprueba que cabe.

    Devuelve None y anota el motivo cuando no cabe: se pierde ESE valor, no la fila
    entera. Un perfil con una altura de catenaria imposible sigue siendo un perfil.
    """
    # El '0' se trata como hueco, igual que en el resto del generador: el origen lo
    # usa como marcador de celda vacia en TODAS las columnas. Cargarlo como un cero
    # de verdad meteria miles de medidas inventadas indistinguibles de las reales.
    if is_blank(value):
        return None, False

    number = to_decimal(value)
    if number is None:
        master.discard(motivo=f"{field}: no es un numero", ep=ep, hoja=sheet,
                       fila=row, detalle=squash(value))
        return None, True

    integers, decimals, minimum = NUMERIC_LIMITS[field]
    number = number.quantize(Decimal(1).scaleb(-decimals))

    if minimum is not None and number < minimum:
        master.discard(motivo=f"{field}: negativo", ep=ep, hoja=sheet, fila=row,
                       detalle=str(number))
        return None, True

    if field == "ARM_ANGLE" and not (ARM_ANGLE_MIN <= number <= ARM_ANGLE_MAX):
        master.discard(motivo="ARM_ANGLE: fuera de +-90", ep=ep, hoja=sheet,
                       fila=row, detalle=str(number))
        return None, True

    if abs(number) >= Decimal(10) ** integers:
        master.discard(motivo=f"{field}: no cabe en NUMERIC({integers + decimals},{decimals})",
                       ep=ep, hoja=sheet, fila=row, detalle=str(number))
        return None, True

    return number, False


def split_steady_arm(raw, arm_types):
    """Parte 'PH-1150' en tipo y longitud. Devuelve (tipo, longitud, motivo).

    El catalogo SteadyArmType solo tiene el tipo base, pero el origen escribe tipo y
    longitud juntos. La regla es "sufijo numerico = longitud", y necesita la lista de
    tipos porque 'PH-C' y 'PH-Q' llevan guion sin ser una longitud.

    5.594 de las 9.921 celdas medidas traen SOLO el tipo. No es un error: la longitud
    no se conoce, y por eso steady_arm.length es opcional.
    """
    text = squash(raw).replace(" ", "").replace("_", "-")   # 'PH- 1450', 'BTC_1651'
    if not text or is_noise(text):
        return None, None, None

    by_upper = {t.upper(): t for t in arm_types}
    if text.upper() in by_upper:
        return by_upper[text.upper()], None, None

    # Una letra suelta al final ('PHC-1500E') no es parte de la longitud. Se ignora,
    # pero se dice: si algun dia significa algo, esta a la vista en DESCARTADOS.
    suffix = ""
    if len(text) > 1 and text[-1].isalpha() and text[-2].isdigit():
        text, suffix = text[:-1], text[-1]

    index = len(text)
    while index > 0 and text[index - 1].isdigit():
        index -= 1
    digits = text[index:]
    if not digits:
        return None, None, f"tipo de brazo desconocido: {text!r}"

    head = text[:index].rstrip("-")
    if head.upper() not in by_upper:
        return None, None, f"tipo de brazo desconocido: {head!r} (de {text!r})"

    length = int(digits)
    if not STEADY_ARM_LENGTH_MIN <= length <= STEADY_ARM_LENGTH_MAX:
        return by_upper[head.upper()], None, f"longitud {length} fuera de 1..2000"

    reason = f"sufijo ignorado: {suffix!r}" if suffix else None
    return by_upper[head.upper()], length, reason


# --------------------------------------------------------------------------------
# Lectura de una hoja de trazado
# --------------------------------------------------------------------------------

def resolve_columns(header, cfg, ep, sheet, master: Master):
    """Cabecera -> indices, por NOMBRE. Devuelve (simples, mensula) o (None, None)."""
    single_map = {norm_header(k): v for k, v in cfg["profile_columns"]["profile"].items()}
    multi_map = {norm_header(k): v for k, v in cfg["profile_columns"]["cantilever"].items()}
    ignored = {norm_header(v) for v in cfg["profile_columns"]["unmapped"]}

    single, multi, unmapped = {}, {}, {}
    for index, value in enumerate(header):
        name = norm_header(value)
        if not name:
            continue
        if name in single_map:
            single.setdefault(single_map[name], index)
        elif name in multi_map:
            # Cabecera combinada sobre tres columnas: M1/M2/M3, D1/D2/D3, ...
            multi.setdefault(multi_map[name], [index + slot for slot in range(CANTILEVER_SLOTS)])
        elif name in ignored:
            unmapped.setdefault(squash(value), index)
        else:
            master.unrecognised("cabecera Track", name, ep, sheet)

    return single, multi, unmapped


def cell(row, index):
    return row[index] if index is not None and index < len(row) else None


def load_lov_catalog(path):
    """Codigos HABILITADOS de lov-master.xlsx, por entidad y en mayusculas.

    Solo los habilitados: un codigo con ENABLED=NO no llega a la base de datos, asi que
    referenciarlo desde un perfil es exactamente igual de roto que inventarselo.
    """
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        sheet = workbook["LOVS"]
        rows = sheet.iter_rows(values_only=True)
        header = list(next(rows))
        i_entity = header.index("ENTIDAD")
        i_code = header.index("CODIGO")
        i_enabled = header.index("ENABLED")
        catalog = collections.defaultdict(set)
        for row in rows:
            if not row or row[i_code] is None:
                continue
            if squash(row[i_enabled]).upper() != "SI":
                continue
            catalog[squash(row[i_entity])].add(squash(row[i_code]).upper())
        return dict(catalog)
    finally:
        workbook.close()


def resolve_lov(field, text, cfg, ep, sheet, row, master: Master):
    """Canonicaliza un codigo y comprueba que el catalogo lo tiene habilitado.

    Devuelve el codigo canonico. Si no resuelve, lo deja tal cual —tirarlo escondería el
    problema— y lo anota en NO_RECONOCIDO, que es lo que hace terminar con codigo != 0.
    """
    if not text:
        return text

    entity = LOV_ENTITY.get(field)
    if entity is None:
        return text

    # 'NON DEFINED', 'N.D.', 'U.S. N.D.', 'PENDIENTE'... no son codigos: son la forma de
    # escribir "aqui no hay dato". La misma lista que impide que entren en el catalogo
    # (code_rejections) los convierte aqui en el hueco que son. Dejarlos pasar los sacaria
    # en NO_RECONOCIDO como si faltara una LOV por declarar, que es justo lo contrario.
    if any(squash(marker).upper() == text.upper() for marker in cfg.get("code_rejections", [])):
        return ""

    # La misma tabla que usa build_lov_master.py: si alli 'FW25' es 'FW-25', aqui tambien.
    # Tenerla en un solo sitio y aplicarla en uno solo era el fallo: el catalogo quedaba
    # canonicalizado y las referencias de los perfiles no.
    table = cfg.get("code_canonical", {}).get(entity, {})
    upper = text.upper()
    for raw, canonical in table.items():
        if squash(raw).upper() == upper:
            text, upper = canonical, canonical.upper()
            break

    catalog = cfg.get("lov_catalog")
    if catalog is not None and upper not in catalog.get(entity, set()):
        master.unrecognised(f"codigo sin {entity} habilitado", text, ep, sheet, row)
    return text


def read_track(ws, ep, decl, cfg, master: Master):
    """Vuelca una hoja (o el tramo declarado de una hoja) al maestro."""
    sheet = ws.title
    track_name = decl["name"]
    rows = list(ws.iter_rows(min_row=1, max_row=min(ws.max_row, MAX_ROWS_TRACK),
                             max_col=TRACK_DATA_MAX_COL, values_only=True))
    header_index = find_header_row(rows)
    if header_index is None:
        master.discard(motivo="hoja sin cabecera reconocible", ep=ep, hoja=sheet, fila=1)
        return 0, 0

    single, multi, unmapped_cols = resolve_columns(rows[header_index], cfg, ep, sheet, master)
    if "PROFILE_ID" not in single:
        master.unrecognised("hoja sin columna PROFILE", sheet, ep, sheet, header_index + 1)
        return 0, 0

    # La fila siguiente a la cabecera es la subcabecera de grupos (M1/M2/M3): no trae
    # identificador de perfil, asi que se descarta sola por el filtro de mas abajo.
    start, end = header_index + 1, len(rows)
    if decl.get("rows"):
        declared_first, declared_last = decl["rows"]
        start, end = max(start, declared_first - 1), min(end, declared_last)

    profiles = cantilevers = 0
    # (via, profileId) es la clave natural del perfil. El origen la repite alguna vez
    # dentro de una misma via —no por tramos concatenados, que se cortan con 'rows',
    # sino por duplicados de verdad—, y dos filas con la misma clave chocarian al
    # importar. Se carga la primera y la siguiente sale marcada.
    seen_ids = set()

    for offset in range(start, end):
        row = rows[offset]
        raw_id = cell(row, single["PROFILE_ID"])
        code = squash(raw_id)
        if is_blank(raw_id) or is_noise(code):
            continue

        number = offset + 1                       # el numero que enseña Excel
        review = False
        record = {"EP": ep, "VIA": track_name, "PROFILE_ID": code[:50],
                  "HOJA_ORIGEN": sheet, "FILA_ORIGEN": number}
        if len(code) > 50:
            master.discard(motivo="PROFILE_ID mas largo de 50", ep=ep, hoja=sheet,
                           fila=number, detalle=code)
            review = True

        kp, bad = fit_numeric("KP", cell(row, single.get("KP")), ep=ep, sheet=sheet,
                              row=number, master=master)
        record["KP"] = kp
        review = review or bad

        # El vano vive en la fila INTERMEDIA, entre este perfil y el siguiente.
        span_value = cell(row, single.get("SPAN"))
        if is_blank(span_value):
            span_value = lookahead(rows, offset, end, single, "SPAN")
        span, bad = fit_numeric("SPAN", span_value, ep=ep, sheet=sheet, row=number,
                                master=master)
        record["SPAN"] = span
        review = review or bad

        for field in ("HEIGHT_CANTILEVER_SUPPORT", "POLE_GAUGE_LOCATION", "RAIL_POLE_DISTANCE"):
            value, bad = fit_numeric(field, cell(row, single.get(field)), ep=ep,
                                     sheet=sheet, row=number, master=master)
            record[field] = value
            review = review or bad

        for field in PROFILE_LOV_FIELDS:
            raw = cell(row, single.get(field))
            text = squash(raw)
            text = "" if is_blank(raw) or is_noise(text) else text
            record[field] = resolve_lov(field, text, cfg, ep, sheet, number, master)

        duplicated = code.upper() in seen_ids
        if duplicated:
            master.discard(motivo="PROFILE_ID repetido dentro de la via", ep=ep,
                           hoja=sheet, fila=number, detalle=f"{track_name} / {code}")
            review = True
        seen_ids.add(code.upper())

        record["PROFILE_STATUS"] = cfg_default_status(cfg, ep)
        record["ENABLED"] = ("SI" if (record["PROFILE_ID"] and kp is not None
                                      and not duplicated) else "NO")
        record["REVISAR"] = "SI" if review or record["ENABLED"] == "NO" else "NO"
        master.profiles.append(record)
        profiles += 1

        cantilevers += read_cantilevers(row, multi, cfg, record, ep, sheet,
                                        number, master)
        collect_unmapped(rows, offset, end, unmapped_cols, record, master)

    return profiles, cantilevers


def lookahead(rows, offset, end, single, field):
    """Primer valor del campo en las filas intermedias hasta el perfil siguiente."""
    index = single.get(field)
    if index is None:
        return None
    for ahead in range(offset + 1, end):
        row = rows[ahead]
        if not is_blank(cell(row, single["PROFILE_ID"])):
            return None
        value = cell(row, index)
        if not is_blank(value):
            return value
    return None


def read_cantilevers(row, multi, cfg, profile, ep, sheet, number, master: Master):
    """Hasta tres mensulas por perfil, una por slot ocupado."""
    arm_types = cfg["steady_arm_types"]
    written = 0
    for slot in range(CANTILEVER_SLOTS):
        values = {}
        for field, indexes in multi.items():
            values[field] = cell(row, indexes[slot])

        if all(is_blank(v) for v in values.values()):
            continue

        review = False
        record = {"EP": ep, "VIA": profile["VIA"], "PROFILE_ID": profile["PROFILE_ID"],
                  "SLOT": slot + 1, "FILA_ORIGEN": number}

        raw_type = values.get("CANTILEVER_TYPE")
        type_code = squash(raw_type)
        type_code = "" if is_blank(raw_type) or is_noise(type_code) else type_code
        record["CANTILEVER_TYPE"] = resolve_lov("CANTILEVER_TYPE", type_code, cfg, ep,
                                                sheet, number, master)

        for field in ("STAGGER", "CATENARY_HEIGHT", "CW_ELEVATION", "CW_HEIGHT",
                      "WIND_DEFLECTION", "ARM_ANGLE"):
            value, bad = fit_numeric(field, values.get(field), ep=ep, sheet=sheet,
                                     row=number, master=master)
            record[field] = value
            review = review or bad

        arm_type, arm_length, reason = split_steady_arm(values.get("STEADY_ARM"), arm_types)
        if reason:
            master.discard(motivo=f"STEADY_ARM: {reason}", ep=ep, hoja=sheet,
                           fila=number, detalle=squash(values.get("STEADY_ARM")))
            review = True
        record["STEADY_ARM_TYPE"] = resolve_lov("STEADY_ARM_TYPE", arm_type or "", cfg,
                                                ep, sheet, number, master)
        record["STEADY_ARM_LENGTH"] = arm_length

        record["ENABLED"] = "SI" if record["CANTILEVER_TYPE"] else "NO"
        record["REVISAR"] = "SI" if review or record["ENABLED"] == "NO" else "NO"
        master.cantilevers.append(record)
        written += 1

    return written


def collect_unmapped(rows, offset, end, unmapped_cols, profile, master: Master):
    """Guarda las columnas reales que hoy no tienen campo en el dominio.

    No se descartan: conservarlas aqui hace que ampliar el modelo mas adelante sea un
    cambio de esquema y un mapper, y no volver a analizar 60 MB de Excel.
    """
    for label, index in unmapped_cols.items():
        value = rows[offset][index] if index < len(rows[offset]) else None
        if is_blank(value):
            continue
        master.unmapped.append({
            "EP": profile["EP"], "VIA": profile["VIA"],
            "PROFILE_ID": profile["PROFILE_ID"], "COLUMNA": label,
            "VALOR": squash(value), "FILA_ORIGEN": profile["FILA_ORIGEN"],
        })


def cfg_default_status(cfg, ep):
    """Estado por defecto del perfil, con la posibilidad de fijarlo por EP."""
    per_ep = cfg.get("execution_packages", {}).get(ep, {}).get("profile_status")
    return per_ep or cfg.get("defaults", {}).get("profile_status", "DEFINITIVE")


# --------------------------------------------------------------------------------
# Siembra de topology.yml
# --------------------------------------------------------------------------------

def sheet_title(ws):
    """Titulo legible de la celda A1, que expande las abreviaturas del nombre de hoja.

    'HR Track 1 HER' -> 'TRACK 1 HERZLIYA'. Es una SEMILLA, no una fuente de verdad:
    EP14B/HR Track 6 dice 'TRACK 5' y EP4/HR TRACK 4 KFA dice 'TRACK 04 KFA'.
    """
    for row in ws.iter_rows(min_row=1, max_row=1, max_col=6, values_only=True):
        for value in row:
            text = squash(value)
            if text:
                return text
    return ""


def seed_topology(folder, output):
    """Escribe un topology.yml inicial para que un humano lo corrija.

    Todo sale marcado: el nombre viene de A1, que miente en cuatro hojas, y la
    estacion no se puede deducir de ninguna parte.
    """
    files = discover(folder)
    if not files:
        print(f"ERROR: no hay workbooks en {folder}", file=sys.stderr)
        return 2

    lines = [
        "# Declaracion de la topologia de los workbooks.",
        "#",
        "# Aqui vive lo que NO esta en los ficheros y solo puede decir una persona:",
        "# los metadatos de cada Execution Package, que via es cada hoja, si esa via",
        "# cuelga de una estacion o directamente del paquete, y donde se parte una hoja",
        "# que en realidad lleva dos tramos concatenados.",
        "#",
        "# Generado con --seed-topology. TODAS las entradas necesitan repaso:",
        "#   - 'name' viene de la celda A1, que miente en cuatro hojas.",
        "#   - 'station: null' es el valor de arranque, NO una afirmacion. La columna",
        "#     TRACK.STATION_ID es anulable a proposito, asi que null es una respuesta",
        "#     valida: la via cuelga del paquete de ejecucion.",
        "#   - 'rows: [primera, ultima]' solo hace falta cuando una hoja lleva mas de un",
        "#     tramo. EP9A / HR Track 1 y HR Track 2 son el caso conocido.",
        "#   - 'skip: motivo' para una hoja que no hay que importar.",
        "#",
        "# Una hoja HR Track sin declarar hace terminar el generador con codigo != 0.",
        "",
        "defaults:",
        "  profile_status: DEFINITIVE",
        "",
        "execution_packages:",
    ]

    for path in files:
        ep = os.path.splitext(os.path.basename(path))[0]
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        try:
            lines += [
                f"  {ep}:",
                f"    file: {os.path.basename(path)}",
                f'    name: "{ep}"                  # REVISAR: nombre real del paquete',
                "    initial_package: false        # REVISAR",
                "    length: 0                     # REVISAR: longitud en metros",
                "    start_date: 1970-01-01        # REVISAR",
                "    end_date: 1970-01-01          # REVISAR",
                '    company_identification_number: ""   # REVISAR: NIF de la empresa',
                "    stations: []                  # REVISAR: estaciones del paquete",
                "    tracks:",
            ]
            for ws in wb.worksheets:
                if not is_track_sheet(ws.title):
                    continue
                title = sheet_title(ws) or ws.title
                lines += [
                    f'      - sheet: "{ws.title}"',
                    f'        name: "{title}"          # REVISAR: derivado de A1',
                    "        station: null            # REVISAR",
                ]
        finally:
            wb.close()

    with open(output, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"Escrito {output}. Repasa las entradas marcadas REVISAR antes de generar.")
    return 0


# --------------------------------------------------------------------------------
# Escritura del maestro
# --------------------------------------------------------------------------------

SHEETS = {
    "EPS": ["EP", "NOMBRE", "INITIAL_PACKAGE", "LENGTH", "START_DATE", "END_DATE",
            "COMPANY_ID_NUMBER", "ENABLED"],
    "STATIONS": ["EP", "NOMBRE"],
    "TRACKS": ["EP", "NOMBRE", "ESTACION", "ENABLED", "HOJA_ORIGEN", "FILA_INICIO", "FILA_FIN"],
    "PROFILES": ["EP", "VIA", "PROFILE_ID", "KP", "PROFILE_STATUS",
                 "SECTIONING", "ANCHORAGE", "ANCHORAGE_FOUNDATION", "FOUNDATION",
                 "POLE_TYPE", "PORTAL", "RETURN_SUPPORT", "SECTIONING_FEEDING",
                 "SPAN", "HEIGHT_CANTILEVER_SUPPORT", "POLE_GAUGE_LOCATION",
                 "RAIL_POLE_DISTANCE", "ENABLED", "REVISAR", "HOJA_ORIGEN", "FILA_ORIGEN"],
    "CANTILEVERS": ["EP", "VIA", "PROFILE_ID", "SLOT", "CANTILEVER_TYPE", "STAGGER",
                    "CATENARY_HEIGHT", "CW_ELEVATION", "CW_HEIGHT", "WIND_DEFLECTION",
                    "ARM_ANGLE", "STEADY_ARM_TYPE", "STEADY_ARM_LENGTH",
                    "ENABLED", "REVISAR", "FILA_ORIGEN"],
    # Las dos costuras para lo que falta: se escriben con cabecera y sin filas.
    "DISCONNECTORS": ["EP", "ESTACION", "VIA", "PROFILE_ID", "NOMBRE", "ON_LOAD",
                      "DISCONNECTOR_FUNCTION", "ENABLED"],
    "SECTION_INSULATORS": ["EP", "ESTACION", "NOMBRE", "ENABLED"],
    "NO_MAPEADO": ["EP", "VIA", "PROFILE_ID", "COLUMNA", "VALOR", "FILA_ORIGEN"],
    "DESCARTADOS": ["MOTIVO", "EP", "HOJA", "FILA", "DETALLE"],
    "NO_RECONOCIDO": ["TIPO", "VALOR", "EP", "HOJA", "PRIMERA_FILA", "APARICIONES"],
}

READ_ME = [
    ("CONCEPTO", "EXPLICACION"),
    ("Que es este fichero",
     "Maestro de perfiles generado desde data/workbook. Es lo unico que lee la aplicacion."),
    ("Como se regenera", "python3 data/tools/build_profile_master.py"),
    ("ENABLED", "SI/NO. Es lo unico que decide si la fila se carga en BBDD."),
    ("REVISAR", "SI cuando hace falta una decision humana. La fila sale resaltada."),
    ("HOJA_ORIGEN / FILA_ORIGEN",
     "Hoja y numero de fila del workbook, tal como los enseña Excel, para poder ir a la celda."),
    ("SPAN", "Vano HASTA EL PERFIL SIGUIENTE, en metros. En el origen vive en la fila intermedia."),
    ("SLOT", "1, 2 o 3. Posicion de la mensula dentro del perfil (columnas M1/M2/M3)."),
    ("STEADY_ARM_TYPE / LENGTH",
     "La columna 'Arm Type' del origen trae los dos juntos ('PH-1150'). Sin longitud es normal."),
    ("NO_MAPEADO",
     "Columnas reales del origen que hoy no tienen campo en el dominio. No se importan."),
    ("DESCARTADOS", "Todo lo rechazado, con el motivo y la celda de origen."),
    ("NO_RECONOCIDO",
     "Lo que el generador no supo mapear. Si tiene filas, el maestro esta incompleto."),
]


def write_sheet(wb, name, headers, rows, review_key=None):
    ws = wb.create_sheet(name)
    ws.append(headers)
    for index in range(1, len(headers) + 1):
        cell_ = ws.cell(row=1, column=index)
        cell_.fill, cell_.font = HEADER_FILL, HEADER_FONT
        cell_.alignment = Alignment(horizontal="center")
        ws.column_dimensions[get_column_letter(index)].width = max(12, len(headers[index - 1]) + 2)
    for record in rows:
        ws.append([record.get(header) for header in headers])
        if review_key and record.get(review_key) == "SI":
            for index in range(1, len(headers) + 1):
                ws.cell(row=ws.max_row, column=index).fill = REVIEW_FILL
    ws.freeze_panes = "A2"
    if rows:
        ws.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{ws.max_row}"
    return ws


def write_master(path, master: Master):
    wb = openpyxl.Workbook()
    wb.remove(wb.active)

    ws = wb.create_sheet("LEEME")
    for row in READ_ME:
        ws.append(list(row))
    for index in (1, 2):
        ws.cell(row=1, column=index).fill = HEADER_FILL
        ws.cell(row=1, column=index).font = HEADER_FONT
    ws.column_dimensions["A"].width = 26
    ws.column_dimensions["B"].width = 100

    write_sheet(wb, "EPS", SHEETS["EPS"], master.eps)
    write_sheet(wb, "STATIONS", SHEETS["STATIONS"], master.stations)
    write_sheet(wb, "TRACKS", SHEETS["TRACKS"], master.tracks)
    write_sheet(wb, "PROFILES", SHEETS["PROFILES"], master.profiles, review_key="REVISAR")
    write_sheet(wb, "CANTILEVERS", SHEETS["CANTILEVERS"], master.cantilevers, review_key="REVISAR")
    write_sheet(wb, "DISCONNECTORS", SHEETS["DISCONNECTORS"], [])
    write_sheet(wb, "SECTION_INSULATORS", SHEETS["SECTION_INSULATORS"], [])
    write_sheet(wb, "NO_MAPEADO", SHEETS["NO_MAPEADO"], master.unmapped)
    write_sheet(wb, "DESCARTADOS", SHEETS["DESCARTADOS"],
                [{"MOTIVO": d["motivo"], "EP": d["ep"], "HOJA": d["hoja"],
                  "FILA": d["fila"], "DETALLE": d["detalle"]} for d in master.discarded])
    write_sheet(wb, "NO_RECONOCIDO", SHEETS["NO_RECONOCIDO"],
                [{"TIPO": u["tipo"], "VALOR": u["valor"], "EP": u["ep"], "HOJA": u["hoja"],
                  "PRIMERA_FILA": u["primera_fila"], "APARICIONES": u["apariciones"]}
                 for u in master.unknown.values()])

    wb.save(path)


# --------------------------------------------------------------------------------
# Entrada
# --------------------------------------------------------------------------------

def check_declared_stations(ep, declared, master: Master):
    """Comprueba que cada via cuelgue de una estacion declarada en su paquete.

    'station: null' es una respuesta valida y no se toca: la columna TRACK.STATION_ID es
    anulable a proposito y una via de tramo entre estaciones cuelga del paquete.
    """
    stations = {squash(name).upper() for name in (declared.get("stations") or []) if squash(name)}

    for track in declared.get("tracks") or []:
        if track.get("skip"):
            continue
        station = squash(track.get("station"))
        if not station:
            continue
        if station.upper() not in stations:
            master.unrecognised("estacion no declarada en el paquete", station, ep,
                                squash(track.get("sheet")))


def load_declared(topology, ep):
    return topology.get("execution_packages", {}).get(ep)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", nargs="?", default="data/workbook")
    parser.add_argument("-o", "--output", default="data/profile-master.xlsx")
    parser.add_argument("--aliases", default=os.path.join(os.path.dirname(__file__), "aliases.yml"))
    parser.add_argument("--topology", default=os.path.join(os.path.dirname(__file__), "topology.yml"))
    parser.add_argument("--lov-master", default="data/lov-master.xlsx")
    parser.add_argument("--seed-topology", action="store_true",
                        help="escribe un topology.yml inicial y termina")
    args = parser.parse_args()

    if args.seed_topology:
        return seed_topology(args.folder, args.topology)

    with open(args.aliases, encoding="utf-8") as handle:
        cfg = yaml.safe_load(handle)
    if not os.path.exists(args.topology):
        print(f"ERROR: falta {args.topology}. Generalo con --seed-topology y repasalo.",
              file=sys.stderr)
        return 2
    with open(args.topology, encoding="utf-8") as handle:
        topology = yaml.safe_load(handle) or {}
    cfg = {**cfg, **{k: v for k, v in topology.items() if k in ("defaults", "execution_packages")}}

    if not os.path.exists(args.lov_master):
        print(f"ERROR: falta {args.lov_master}. Generalo con build_lov_master.py: sin el "
              f"catalogo no se puede comprobar que los codigos de los perfiles existan.",
              file=sys.stderr)
        return 2
    cfg["lov_catalog"] = load_lov_catalog(args.lov_master)

    files = discover(args.folder)
    if not files:
        print(f"ERROR: no hay workbooks en {args.folder}", file=sys.stderr)
        return 2

    master = Master()
    print(f"Leyendo {len(files)} workbooks de {args.folder}\n")
    totals = collections.Counter()

    for path in files:
        ep = os.path.splitext(os.path.basename(path))[0]
        declared = load_declared(topology, ep)
        if declared is None:
            master.unrecognised("EP sin declarar en topology.yml", ep, ep, "")
            print(f"  {ep:8} SIN DECLARAR")
            continue

        master.eps.append({
            "EP": ep, "NOMBRE": declared.get("name", ep),
            "INITIAL_PACKAGE": "SI" if declared.get("initial_package") else "NO",
            "LENGTH": declared.get("length"),
            "START_DATE": declared.get("start_date"), "END_DATE": declared.get("end_date"),
            "COMPANY_ID_NUMBER": declared.get("company_identification_number", ""),
            "ENABLED": "SI",
        })
        for station in declared.get("stations") or []:
            master.stations.append({"EP": ep, "NOMBRE": squash(station)})

        # Una hoja puede llevar mas de un tramo, asi que se indexa por nombre de hoja.
        by_sheet = collections.defaultdict(list)
        for track in declared.get("tracks") or []:
            by_sheet[squash(track.get("sheet"))].append(track)

        # Una via no puede inventarse una estacion: si la que declara no esta en la lista
        # del paquete, el importador la rechaza y esa via se queda sin cargar. Vale mas
        # enterarse aqui, generando, que descubrirlo con el trabajo a medias.
        check_declared_stations(ep, declared, master)

        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        sheets = profiles = cantilevers = 0
        try:
            for ws in wb.worksheets:
                if not is_track_sheet(ws.title):
                    continue
                declarations = by_sheet.get(squash(ws.title))
                if not declarations:
                    master.unrecognised("hoja Track sin declarar", ws.title, ep, ws.title)
                    continue
                for decl in declarations:
                    if decl.get("skip"):
                        master.discard(motivo=f"hoja omitida: {decl['skip']}", ep=ep,
                                       hoja=ws.title, fila=1)
                        continue
                    rows = decl.get("rows") or [None, None]
                    master.tracks.append({
                        "EP": ep, "NOMBRE": decl["name"],
                        "ESTACION": decl.get("station") or "",
                        "ENABLED": "SI", "HOJA_ORIGEN": ws.title,
                        "FILA_INICIO": rows[0], "FILA_FIN": rows[1],
                    })
                    written, arms = read_track(ws, ep, decl, cfg, master)
                    profiles += written
                    cantilevers += arms
                sheets += 1
        finally:
            wb.close()

        totals["profiles"] += profiles
        totals["cantilevers"] += cantilevers
        print(f"  {ep:8} hojas={sheets:<3} perfiles={profiles:<6} mensulas={cantilevers}")

    write_master(args.output, master)

    enabled_profiles = sum(1 for p in master.profiles if p["ENABLED"] == "SI")
    enabled_cantilevers = sum(1 for c in master.cantilevers if c["ENABLED"] == "SI")
    print(f"\n{'HOJA':22}{'FILAS':>9}{'ENABLED':>9}")
    print(f"{'EPS':22}{len(master.eps):>9}{len(master.eps):>9}")
    print(f"{'STATIONS':22}{len(master.stations):>9}{len(master.stations):>9}")
    print(f"{'TRACKS':22}{len(master.tracks):>9}{len(master.tracks):>9}")
    print(f"{'PROFILES':22}{len(master.profiles):>9}{enabled_profiles:>9}")
    print(f"{'CANTILEVERS':22}{len(master.cantilevers):>9}{enabled_cantilevers:>9}")
    print(f"{'NO_MAPEADO':22}{len(master.unmapped):>9}")
    print(f"\nDescartados: {len(master.discarded)}")
    print(f"Escrito: {args.output}")

    if master.unknown:
        unknown = list(master.unknown.values())
        print(f"\nATENCION: {len(unknown)} valores NO RECONOCIDOS "
              f"({sum(u['apariciones'] for u in unknown)} apariciones).")
        print("El maestro esta incompleto. Revisa la hoja NO_RECONOCIDO, declara la hoja "
              "en topology.yml o amplia data/tools/aliases.yml:")
        for item in unknown[:15]:
            print(f"  [{item['tipo']}] {item['valor']!r} ({item['ep']}/{item['hoja']}, "
                  f"x{item['apariciones']})")
        return 1

    print("\nSin valores no reconocidos: el maestro esta completo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
