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
    code_tokens,
    MAX_ROWS_TRACK,
    TRACK_DATA_MAX_COL,
    discover,
    find_header_row,
    is_blank,
    is_noise,
    is_track_sheet,
    norm_header,
    normalize_code,
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
# Las columnas multivalor: un perfil puede llevar varios seccionamientos ('A/S P50' son
# dos, corriente en estaciones) y varios anclajes ('FP+AnMC CP+AnMC' es uno con regulacion
# de tension y otro sin ella). En las demas, dos codigos en una celda es una anomalia y por
# eso alli sigue saliendo a NO_RECONOCIDO.
LOV_MULTIVALUE = {"SECTIONING", "ANCHORAGE", "SECTIONING_FEEDING"}

# Separador de las columnas multivalor del maestro. NO es el espacio, y no puede serlo:
# hay codigos del catalogo que LLEVAN espacios —'A/S Diag S/A', 'T-SIGN FOUND.',
# 'CP+AnMC 265,00'—, asi que una celda separada por espacios es ambigua y el importador
# no puede deshacerla. Partiendo 'P30(CS) A/S Diag' —que es UN codigo del catalogo— se
# inventaba un 'Diag' que no existe, y con el se caian 142 perfiles al importar. La barra
# es la misma que ya usa la columna ESTACIONES.
LOV_SEPARATOR = "|"

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
    """Codigos HABILITADOS de lov-master.xlsx: {entidad: {CODIGO_EN_MAYUSCULAS: grafia}}.

    Solo los habilitados: un codigo con ENABLED=NO no llega a la base de datos, asi que
    referenciarlo desde un perfil es exactamente igual de roto que inventarselo.

    Se guarda la GRAFIA EXACTA del catalogo, no solo la clave en mayusculas, porque el
    importador resuelve con LovRepository.findByCode, que es una consulta derivada y por
    tanto SENSIBLE A MAYUSCULAS. Comparar aqui sin distinguirlas y escribir luego la
    grafia del origen dejaria pasar 'DISC/IO-pr' cuando el catalogo dice 'Disc/IO-pr': el
    generador daria el maestro por bueno y el importador rechazaria la fila.
    """
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        sheet = workbook["LOVS"]
        rows = sheet.iter_rows(values_only=True)
        header = list(next(rows))
        i_entity = header.index("ENTIDAD")
        i_code = header.index("CODIGO")
        i_enabled = header.index("ENABLED")
        catalog = collections.defaultdict(dict)
        for row in rows:
            if not row or row[i_code] is None:
                continue
            if squash(row[i_enabled]).upper() != "SI":
                continue
            code = squash(row[i_code])
            catalog[squash(row[i_entity])][code.upper()] = code
        return dict(catalog)
    finally:
        workbook.close()


def resolve_lov(field, text, cfg, ep, sheet, row, master: Master):
    """Canonicaliza un codigo y comprueba que el catalogo lo tiene habilitado.

    Devuelve (codigo, sin_resolver). Los codigos que resuelven salen con la grafia del
    catalogo y, si son varios, unidos por LOV_SEPARATOR.

    El que NO resuelve sale VACIO, no tal cual. Escribirlo daba un maestro que no se
    puede cargar: el importador comprueba cada codigo contra su catalogo —tiene que
    hacerlo, porque MasterDataService resuelve un codigo desconocido a null sin quejarse—
    y tumbaba la fila entera por el valor de una relacion opcional. Vaciarlo no lo
    esconde: sigue anotado en NO_RECONOCIDO con su EP, su hoja y su fila, la fila sale
    marcada REVISAR y el generador sigue terminando con codigo != 0.
    """
    if not text:
        return text, False

    entity = LOV_ENTITY.get(field)
    if entity is None:
        return text, False

    # Multivalor: PRIMERO se prueba la celda entera. Solo si no es un codigo se parte, y
    # solo se acepta la particion cuando TODAS las partes son codigos validos. El orden
    # importa: hay codigos legitimos con espacio dentro ('T-SIGN FOUND.'), y probar la
    # celda entera primero es lo que impide romperlos.
    #
    # La particion la hace code_tokens, la MISMA que limpia el catalogo en
    # build_lov_master.py. Ahi estan las reglas que dice la leyenda de los workbooks:
    # 'A/S Diag' es 'A/S-Diag', el guion suelto separa, el (T1) sobra. Partiendo por
    # espacios a secas, 'P30(CS) A/S Diag' se convertia en 'P30(CS)' + 'A/S' + un 'Diag'
    # que no existe.
    if field in LOV_MULTIVALUE:
        catalog = cfg.get("lov_catalog")
        entero = canonical_code(entity, text, cfg)
        if catalog is None or entero.upper() in catalog.get(entity, {}):
            return resolve_lov_single(field, text, cfg, ep, sheet, row, master)

        # Se trocea 'entero', no 'text': la tabla de grafias puede ser justo lo que
        # separa dos codigos pegados ('FP+AnMCAnRW' -> 'FP+AnMC AnRW'), y partiendo el
        # texto original ese arreglo no llegaria a aplicarse nunca.
        noise = cfg.get("code_noise_tokens", {}).get(entity)
        partes = [canonical_code(entity, part, cfg) for part in code_tokens(entero, noise)]
        conocidos = catalog.get(entity, {})
        if partes and all(part.upper() in conocidos for part in partes):
            vistos = []
            for part in partes:                      # 'S/A S/A' es uno, no dos
                exacto = conocidos[part.upper()]     # la grafia del catalogo, no la del origen
                if exacto.upper() not in {v.upper() for v in vistos}:
                    vistos.append(exacto)
            return LOV_SEPARATOR.join(vistos), False

    return resolve_lov_single(field, text, cfg, ep, sheet, row, master)


def canonical_code(entity, text, cfg):
    """El codigo que declara aliases.yml para esta grafia, o el mismo texto."""
    text = normalize_code(text)
    table = cfg.get("code_canonical", {}).get(entity, {})
    upper = text.upper()
    for raw, canonical in table.items():
        if squash(raw).upper() == upper:
            return canonical
    return text


def resolve_lov_single(field, text, cfg, ep, sheet, row, master: Master):
    """Un solo codigo: canonicaliza y comprueba que el catalogo lo tiene habilitado."""
    if not text:
        return text, False

    entity = LOV_ENTITY[field]

    # 'NON DEFINED', 'N.D.', 'U.S. N.D.', 'PENDIENTE'... no son codigos: son la forma de
    # escribir "aqui no hay dato". La misma lista que impide que entren en el catalogo
    # (code_rejections) los convierte aqui en el hueco que son. Dejarlos pasar los sacaria
    # en NO_RECONOCIDO como si faltara una LOV por declarar, que es justo lo contrario.
    if any(squash(marker).upper() == text.upper() for marker in cfg.get("code_rejections", [])):
        return "", False

    # La misma errata mecanica que corrige el catalogo: 'P50 (CS)' es 'P50(CS)'.
    text = normalize_code(text)

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
    if catalog is None:
        return text, False
    if upper in catalog.get(entity, {}):
        return catalog[entity][upper], False  # la grafia del catalogo: findByCode distingue

    # 'AnM-R AnM-R', 'AnRW/Tunnel AnRW/Tunnel': no son dos valores, es uno escrito dos
    # veces. Se colapsa solo cuando TODAS las partes son identicas, asi que no puede
    # elegir por su cuenta entre dos codigos distintos —eso si es una decision humana— ni
    # romper un codigo que lleve espacio, como 'T-SIGN FOUND.'.
    parts = text.split()
    if len(parts) > 1 and len({p.upper() for p in parts}) == 1:
        single = parts[0]
        if single.upper() in catalog.get(entity, {}):
            master.discard(motivo=f"{entity}: codigo repetido en la celda", ep=ep,
                           hoja=sheet, fila=row, detalle=text)
            return catalog[entity][single.upper()], False

    master.unrecognised(f"codigo sin {entity} habilitado", text, ep, sheet, row)
    return "", True


# El plano de una hoja: sus filas y donde cae cada cosa. Se resuelve UNA vez por hoja
# aunque la hoja lleve varios tramos declarados; antes se resolvia una vez por tramo.
TrackLayout = collections.namedtuple(
    "TrackLayout", "sheet rows header_index single multi unmapped")


def track_layout(ws, ep, cfg, master: Master):
    """Plano de la hoja, o None si no hay cabecera o no hay columna de perfil."""
    sheet = ws.title
    rows = list(ws.iter_rows(min_row=1, max_row=min(ws.max_row, MAX_ROWS_TRACK),
                             max_col=TRACK_DATA_MAX_COL, values_only=True))
    header_index = find_header_row(rows)
    if header_index is None:
        master.discard(motivo="hoja sin cabecera reconocible", ep=ep, hoja=sheet, fila=1)
        return None

    single, multi, unmapped = resolve_columns(rows[header_index], cfg, ep, sheet, master)
    if "PROFILE_ID" not in single:
        master.unrecognised("hoja sin columna PROFILE", sheet, ep, sheet, header_index + 1)
        return None

    return TrackLayout(sheet, rows, header_index, single, multi, unmapped)


def profile_rows(layout: TrackLayout, start=None, end=None):
    """Numeros de fila (los que enseña Excel) que llevan un perfil de verdad."""
    first = layout.header_index + 1 if start is None else start
    last = len(layout.rows) if end is None else end
    found = []
    for offset in range(first, last):
        raw_id = cell(layout.rows[offset], layout.single["PROFILE_ID"])
        if not is_blank(raw_id) and not is_noise(squash(raw_id)):
            found.append(offset + 1)
    return found


def ranges_text(numbers):
    """[121, 122, 123, 130] -> '121-123, 130'. Un listado de 79 filas no lo lee nadie."""
    grupos, inicio, previo = [], None, None
    for number in numbers:
        if inicio is None:
            inicio = previo = number
        elif number == previo + 1:
            previo = number
        else:
            grupos.append((inicio, previo))
            inicio = previo = number
    if inicio is not None:
        grupos.append((inicio, previo))
    return ", ".join(str(a) if a == b else f"{a}-{b}" for a, b in grupos)


def check_sheet_coverage(layout: TrackLayout, ep, declarations, master: Master):
    """Toda fila con perfil tiene que caer en UN tramo declarado. Ni cero, ni dos.

    Solo aplica a las hojas que se cortan con 'rows'. Sin esto, declarar [4, 120] y
    [200, 400] en una hoja que llega a la 400 se traga las 79 filas de en medio sin
    decir nada: el maestro sale con menos perfiles y cuadra consigo mismo, que es la
    peor forma de perder datos. Y al reves, dos tramos que se pisan cargan el mismo
    perfil en dos vias.
    """
    tramos = [(decl["rows"][0], decl["rows"][1]) for decl in declarations
              if decl.get("rows") and not decl.get("skip")]
    if not tramos:
        return

    veces = collections.Counter()
    for first, last in tramos:
        veces.update(range(first, last + 1))

    todas = profile_rows(layout)
    fuera = [number for number in todas if veces[number] == 0]
    repetidas = [number for number in todas if veces[number] > 1]

    if fuera:
        master.unrecognised("filas con perfil fuera de los tramos declarados",
                            ranges_text(fuera), ep, layout.sheet, fuera[0])
    if repetidas:
        master.unrecognised("filas con perfil en dos tramos a la vez",
                            ranges_text(repetidas), ep, layout.sheet, repetidas[0])


def read_track(ws, ep, decl, cfg, master: Master, layout: TrackLayout = None):
    """Vuelca una hoja (o el tramo declarado de una hoja) al maestro."""
    if layout is None:
        layout = track_layout(ws, ep, cfg, master)
        if layout is None:
            return 0, 0

    sheet = layout.sheet
    track_name = decl["name"]
    rows, header_index = layout.rows, layout.header_index
    single, multi, unmapped_cols = layout.single, layout.multi, layout.unmapped

    # La fila siguiente a la cabecera es la subcabecera de grupos (M1/M2/M3): no trae
    # identificador de perfil, asi que se descarta sola por el filtro de mas abajo.
    start, end = header_index + 1, len(rows)
    if decl.get("rows"):
        declared_first, declared_last = decl["rows"]
        start, end = max(start, declared_first - 1), min(end, declared_last)

    profiles = cantilevers = 0
    # (via, profileId, KP) es la clave natural del perfil desde V18. El identificador solo
    # no basta: una via puede llevar dos tramos concatenados con la numeracion reiniciada
    # —'HR Track 1' de EP9A repite 47 identificadores— y ahi '5-1.01' son DOS mastiles, uno
    # en el KP 5421 y otro en el 5017. El KP es lo que los distingue.
    #
    # Lo que sigue chocando es la fila que repite identificador Y kp: eso ya no es un tramo
    # nuevo, es un error del origen (en EP9A hay uno, '8-1.12' en el KP 8447, con distinto
    # tipo de poste en cada aparicion). Se carga la primera y la siguiente sale marcada.
    seen_keys = set()

    # Posicion a lo largo de la via, que es lo que ordena los perfiles desde V18: con dos
    # tramos concatenados el KP no vale, porque el segundo reinicia la kilometracion.
    position = 0

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
            record[field], sin_resolver = resolve_lov(field, text, cfg, ep, sheet,
                                                      number, master)
            review = review or sin_resolver

        key = (code.upper(), None if kp is None else str(kp))
        duplicated = key in seen_keys
        if duplicated:
            master.discard(motivo="PROFILE_ID y KP repetidos dentro de la via", ep=ep,
                           hoja=sheet, fila=number, detalle=f"{track_name} / {code} / kp {kp}")
            review = True
        seen_keys.add(key)

        position += 1
        record["ORDEN"] = position

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

        raw_type = values.get("CANTILEVER_TYPE")
        type_code = squash(raw_type)
        type_code = "" if is_blank(raw_type) or is_noise(type_code) else type_code
        cantilever_type, review = resolve_lov(
            "CANTILEVER_TYPE", type_code, cfg, ep, sheet, number, master)

        # SIN TIPO NO HAY MENSULA, y sin mensula no hay nada suyo que guardar.
        #
        # El slot puede venir con medidas —desviacion, altura de catenaria, hasta un tipo
        # de brazo— y la celda del tipo vacia o con el marcador '-'. Eso no es una mensula
        # a la que le falte un dato: es que ahi NO hay mensula, y lo que aparezca en las
        # demas columnas de ese slot es ruido de la hoja. Si un perfil lleva mensula en M1
        # y M2, un valor suelto en M3 esta mal puesto.
        #
        # Un poste sin ninguna mensula es normal —hace de anclaje, por ejemplo— y entonces
        # no tiene ni parametros de mensula ni brazo. Es el mismo caso, con las tres vacias.
        #
        # Se tira el slot ENTERO, con sus medidas y su brazo, y se anota en DESCARTADOS
        # con lo que se tira: 348 slots del maestro estaban asi, y antes salian como una
        # mensula ENABLED=NO que arrastraba unos valores que no describen nada.
        if not cantilever_type:
            tirado = ", ".join(
                f"{field}={squash(value)}" for field, value in values.items()
                if field != "CANTILEVER_TYPE" and not is_blank(value))
            master.discard(
                motivo="slot sin tipo de mensula: ahi no hay mensula",
                ep=ep, hoja=sheet, fila=number,
                detalle=f"{profile['PROFILE_ID']} slot {slot + 1}"
                        + (f": se tira {tirado}" if tirado else ""))
            continue

        # ORDEN y no solo PROFILE_ID: una via con dos tramos concatenados repite el
        # identificador, asi que sin el orden las mensulas de los DOS perfiles se
        # agruparian juntas y cada uno se llevaria las del otro.
        record = {"EP": ep, "VIA": profile["VIA"], "PROFILE_ID": profile["PROFILE_ID"],
                  "ORDEN": profile["ORDEN"], "SLOT": slot + 1, "FILA_ORIGEN": number,
                  "CANTILEVER_TYPE": cantilever_type}

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
        record["STEADY_ARM_TYPE"], sin_resolver = resolve_lov(
            "STEADY_ARM_TYPE", arm_type or "", cfg, ep, sheet, number, master)
        review = review or sin_resolver
        record["STEADY_ARM_LENGTH"] = arm_length

        # Aqui ya hay tipo; lo que queda por mirar es el perfil. El importador agrupa las
        # mensulas por (EP, via, ORDEN) y solo escribe las del perfil que esta cargando,
        # asi que una mensula de un perfil ENABLED=NO no llegaba nunca a la base de datos
        # y el maestro seguia contandola como cargable: cuatro decian SI y no podian entrar.
        record["ENABLED"] = "SI" if profile["ENABLED"] == "SI" else "NO"
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
        "#   - 'station: null' es el valor de arranque, NO una afirmacion. Sin estacion es",
        "#     una respuesta valida: la via cuelga del paquete de ejecucion.",
        "#   - una via LARGA atraviesa varias estaciones sin dejar de ser una via, y se",
        "#     declaran en plural: 'stations: [ZIC, BIN, HAD]'. El singular 'station: ZIC'",
        "#     sigue valiendo para la via que solo pasa por una.",
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
    "TRACKS": ["EP", "NOMBRE", "ESTACIONES", "ENABLED", "HOJA_ORIGEN", "FILA_INICIO", "FILA_FIN"],
    "PROFILES": ["EP", "VIA", "PROFILE_ID", "KP", "ORDEN", "PROFILE_STATUS",
                 "SECTIONING", "ANCHORAGE", "ANCHORAGE_FOUNDATION", "FOUNDATION",
                 "POLE_TYPE", "PORTAL", "RETURN_SUPPORT", "SECTIONING_FEEDING",
                 "SPAN", "HEIGHT_CANTILEVER_SUPPORT", "POLE_GAUGE_LOCATION",
                 "RAIL_POLE_DISTANCE", "ENABLED", "REVISAR", "HOJA_ORIGEN", "FILA_ORIGEN"],
    "CANTILEVERS": ["EP", "VIA", "PROFILE_ID", "ORDEN", "SLOT", "CANTILEVER_TYPE", "STAGGER",
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
    ("Un slot sin tipo no sale",
     "Si la celda de tipo de mensula viene vacia o con '-', ahi no hay mensula: el slot "
     "entero se descarta con sus medidas y su brazo, y sale en DESCARTADOS. Un poste sin "
     "mensulas es normal, hace de anclaje."),
    ("SECTIONING / ANCHORAGE / SECTIONING_FEEDING",
     "Admiten VARIOS codigos, separados por '|'. No por espacio: hay codigos del catalogo "
     "que llevan espacios ('A/S Diag S/A', 'T-SIGN FOUND.'). Sin barra, uno solo."),
    ("Codigo vacio con REVISAR=SI",
     "El origen traia un codigo que el catalogo no tiene habilitado. Sale en NO_RECONOCIDO "
     "con su hoja y su fila; el resto del perfil se carga igual."),
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

def track_stations(track):
    """Las estaciones que declara una via. Cualquiera de las cuatro formas vale.

        station:  ZIC                 stations: [ZIC, BIN, HAD]
        station:  [ZIC, BIN, HAD]     stations: ZIC

    Las dos claves y las dos formas, a proposito. 'station' en singular es lo que hay en
    las vias rellenas antes de V17 y no hay por que reescribirlas; y una lista bajo la
    clave en singular es lo que sale solo al rellenar a mano, sin que la intencion sea
    dudosa. La alternativa era que 27 vias del fichero acabaran con una estacion llamada
    "['MOM', 'PMO']", que no existe y que solo se habria visto al fallar la comprobacion.
    """
    nombres = []
    for clave in ("station", "stations"):
        valor = track.get(clave)
        if valor is None:
            continue
        for nombre in (valor if isinstance(valor, (list, tuple)) else [valor]):
            nombre = squash(nombre)
            if nombre and nombre.upper() not in {n.upper() for n in nombres}:
                nombres.append(nombre)
    return nombres


def check_declared_stations(ep, declared, master: Master):
    """Comprueba que cada via cuelgue de estaciones declaradas en su paquete.

    Sin estacion es una respuesta valida y no se toca: una via de tramo entre estaciones
    cuelga del paquete. Se miran TODAS las que declara, que desde V17 pueden ser varias.
    """
    stations = {squash(name).upper() for name in (declared.get("stations") or []) if squash(name)}

    for track in declared.get("tracks") or []:
        if track.get("skip"):
            continue
        for station in track_stations(track):
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
                layout = track_layout(ws, ep, cfg, master)
                if layout is None:
                    continue
                check_sheet_coverage(layout, ep, declarations, master)
                for decl in declarations:
                    if decl.get("skip"):
                        master.discard(motivo=f"hoja omitida: {decl['skip']}", ep=ep,
                                       hoja=ws.title, fila=1)
                        continue
                    rows = decl.get("rows") or [None, None]
                    master.tracks.append({
                        "EP": ep, "NOMBRE": decl["name"],
                        # Barra vertical y no espacio: 'TLV SAVIDOR' lleva un espacio dentro.
                        "ESTACIONES": " | ".join(track_stations(decl)),
                        "ENABLED": "SI", "HOJA_ORIGEN": ws.title,
                        "FILA_INICIO": rows[0], "FILA_FIN": rows[1],
                    })
                    written, arms = read_track(ws, ep, decl, cfg, master, layout)
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
