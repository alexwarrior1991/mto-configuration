"""Reglas de lectura del formato sinoptico (RUBI): una tabla con las vias en columnas emparejadas.

Las hojas se construyen en memoria con la misma hoja de mentira que usan los tests del
maestro de perfiles. Las tablas de traduccion son las reales de aliases.yml: lo que se
prueba es justamente que una grafia portuguesa del origen NO llega al maestro.

    python3 -m unittest discover -s data/tools/tests
"""

import collections
import importlib.util
import os
import unittest
from decimal import Decimal

import yaml

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.dirname(BASE)


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(BASE, name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bpm = _load("build_profile_master")
blm = _load("build_lov_master")
synoptic = _load("synoptic")

with open(os.path.join(BASE, "aliases.yml"), encoding="utf-8") as handle:
    ALIASES = yaml.safe_load(handle)


class FakeSheet:
    """Lo minimo de una hoja de openpyxl que usa el lector."""

    def __init__(self, title, rows):
        self.title = title
        self._rows = rows
        self.max_row = len(rows)

    def iter_rows(self, min_row=1, max_row=None, max_col=None, values_only=True):
        end = min(max_row or len(self._rows), len(self._rows))
        for row in self._rows[min_row - 1:end]:
            yield tuple(row[:max_col]) if max_col else tuple(row)


# La cabecera real, reducida: una tabla con cada campo en una columna por via. Estan todas
# las maneras de decir la via: el sufijo ' V1' / ' V2', '(via 1)', la cabecera repetida sin
# decirla ('Vão (m)': por posicion) y la columna del apoyo de la via 2 dos veces. Tambien lo
# que no se lee: el relleno 'VIA 2' / 'VIA 1', las columnas '(traçado Jul.2026)' y una tabla
# pegada a la derecha con su columna sin cabecera.
HEADER = [
    "SUPORTE V2", "VIA 2", 1, "VIA 1",
    "SUPORTE V1", "SUPORTE V2", "Vão (m)", "Vão (m)", None,
    "P.K.\n(via 1)", "P.K. \n(via 2)", "P.K.\nAbs.via 1", "P.K.\nAbs.via 2",
    "Maciços V1", "Maciços V2", "Maciços\n(traçado Jul.2026) V1", None,
    "Tipología V1", "Tipología V2", "Implantação (m) V1", "Implantação (m) V2",
    "Tipologia V1", "Tipologia V2", "Tipologia\n(traçado Jul.2026) V1",
    "Tipo de Suporte V1", "Tipo de Suporte V2",
    "Consolas V1", "Consolas V2", "Consolas 2 V1", "Consolas 2 V2",
    "Configuraciones V1", "Configuraciones V2", "Alt. FC (m) V1", "Alt. FC (m) V2",
    "Desalinhamentos (cm) V1", "Desalinhamentos (cm) V2",
    "Desalinhamentos2 (cm) V1", "Desalinhamentos2 (cm) V2",
    "Seccionamento V1", "Seccionamento V2",
    "Equipamentos associados V1", "Equipamentos associados V2",
    "Equipamentos associados 2 V1", "Equipamentos associados 2 V2",
    "Cabos feeder longitudinais V1", None,
    "Verificação dimensional canalização trajectoria Feeder", None,
    "Activo", "Tipología",
]
NAMES = [" ".join(str(name).split()) if name is not None else None for name in HEADER]
FIRST_ID_V2 = 0                                   # la columna A: 'SUPORTE V2' otra vez
BAND = NAMES.index("1")
LOOSE = NAMES.index("Vão (m)") + 2                # sin cabecera, detras de un campo
JUL_2026 = NAMES.index("Maciços (traçado Jul.2026) V1")
FEEDER_2 = NAMES.index("Cabos feeder longitudinais V1") + 1
SIDE_TABLE = NAMES.index("Verificação dimensional canalização trajectoria Feeder")
SHARED_TYPOLOGY = len(NAMES) - 1                  # 'Tipología' sin via: TIPO R / TIPO D e E


def column(name, track=None):
    """Indice de una columna por su cabecera; con ``track``, la de esa via (1 o 2)."""
    if track is None:
        return NAMES.index(name)
    tagged = "%s V%d" % (name, track)
    if tagged in NAMES:
        return NAMES.index(tagged, 1)             # la A es la copia de 'SUPORTE V2'
    for spelling in ("%s (via %d)", "%svia %d"):
        if spelling % (name, track) in NAMES:
            return NAMES.index(spelling % (name, track))
    return [index for index, value in enumerate(NAMES) if value == name][track - 1]


def row(v1=None, v2=None, **loose):
    """Una fila con el relleno del origen y lo que se pida por via y cabecera."""
    values = [None] * len(HEADER)
    values[1], values[3] = "VIA 2", "VIA 1"
    for track, cells in ((1, v1 or {}), (2, v2 or {})):
        for name, value in cells.items():
            values[column(name, track)] = value
            if name == "SUPORTE" and track == 2:
                values[FIRST_ID_V2] = value
    for index, value in loose.items():
        values[int(index[1:])] = value
    return values


def both(**cells):
    """Lo mismo en las dos vias."""
    cells = {name.replace("_", " "): value for name, value in cells.items()}
    return {"v1": dict(cells), "v2": dict(cells)}


RIGID = {"Tipología": "SE", "Tipologia": "Rígida", "Tipo de Suporte": "C860-A",
         "Desalinhamentos (cm)": -12, "Alt. FC (m)": 3.8, "Desalinhamentos2 (cm)": "+20",
         "Seccionamento": "Cantón 290 m"}
CANTILEVER = {"Tipología": "Elev.", "Tipologia": "Consola", "Tipo de Suporte": "Pendulo 140",
              "Maciços": "Anc. Sot.", "Implantação (m)": "1,5", "Desalinhamentos (cm)": "+10",
              "Alt. FC (m)": 3.83, "Consolas": "B. Fun.", "Configuraciones": 0}
FLEXIBLE = {"Tipología": "S/E A/S", "Tipologia": "Funicular", "Tipo de Suporte": "-",
            "Maciços": "Fixaçao Muro", "Desalinhamentos (cm)": "+20", "Alt. FC (m)": 3.8,
            "Consolas": "Delta", "Consolas 2": "B.Fun.", "Desalinhamentos2 (cm)": "+10",
            "Configuraciones": "C.C,2", "Equipamentos associados": "Ancoraem 2HC",
            "Equipamentos associados 2": "Caix. Imped."}

ROWS = [
    ["Linha Rubi"] + [None] * (len(HEADER) - 1),
    HEADER,
    # Fila 3: hito de trazado, sin KP.
    row({"SUPORTE": "Existente", "P.K.": "??"}, {"SUPORTE": "Existente", "P.K.": "??"}),
    # Fila 4: KP negativo.
    row({"SUPORTE": "1-00.01", "P.K.": -2.84, "Tipologia": "Rígida", "Alt. FC (m)": 3.8},
        {"SUPORTE": "1-00.02", "P.K.": -2.84, "Tipologia": "Rígida", "Alt. FC (m)": 3.8}),
    # Fila 5: un hito escrito en la fila intermedia, con el vano del apoyo anterior.
    row({"SUPORTE": "Fim Cais Casa Da Música (00+040)", "Vão (m)": 6.2},
        {"SUPORTE": "Fim Cais Casa Da Música (00+040)", "Vão (m)": 6.2}),
    # Fila 6: catenaria rigida con solape y segunda mensula en 'Desalinhamentos2'. Trae
    # ademas lo que no se lee ('traçado Jul.2026', la tabla de la derecha) y lo que no es
    # de ninguna via ('Activo', 'Tipología' sin sufijo, la banda).
    row(dict(RIGID, SUPORTE="3504CCA19", **{"P.K.": 1061, "P.K. Abs.": 33744.6, "Vão (m)": 4,
                                             "Cabos feeder longitudinais": "2 FA"}),
        dict(RIGID, SUPORTE="3504CCA20", **{"P.K.": 1059.2}),
        **{"c%d" % JUL_2026: "fuera del plano", "c%d" % (JUL_2026 + 1): 2,
           "c%d" % SIDE_TABLE: "TRAJECTOS", "c%d" % (SIDE_TABLE + 1): "Origem - destino",
           "c%d" % FEEDER_2: "3 N", "c%d" % BAND: " CAT D", "c%d" % LOOSE: 13.08,
           "c%d" % column("Activo"): 3504, "c%d" % SHARED_TYPOLOGY: "TIPO R"}),
    row({"Vão (m)": 10, "Seccionamento": "Seccionamento"},
        {"Vão (m)": 10, "Seccionamento": "Seccionamento"},
        **{"c%d" % column("Activo"): 3504, "c%d" % SHARED_TYPOLOGY: "TIPO R"}),
    # Fila 8: catenaria de mensula con KP en kilometracion y todo en portugues.
    row(dict(CANTILEVER, SUPORTE="3505CCA01A", **{"P.K.": 1120.2}),
        dict(CANTILEVER, SUPORTE="3505CCA02", **{"P.K.": "1+118,8"})),
    row({"Vão (m)": 10.6, "Equipamentos associados": "Disp. Transic.",
         "Seccionamento": "Transição Catenaria"},
        {"Vão (m)": 10.6, "Equipamentos associados": "Disp. Transic.",
         "Seccionamento": "Transição Catenaria"}),
    # Fila 10: catenaria flexible con dos mensulas, funcion doble y equipos repartidos.
    row(dict(FLEXIBLE, SUPORTE="3505CCA07", **{"P.K.": 1146.96, "Vão (m)": 6.3}),
        dict(FLEXIBLE, SUPORTE="3505CCA08", **{"P.K.": 1145.16})),
    # Fila 11: la errata 'Anc' y un anclaje de conexion.
    row({"SUPORTE": "3713CCA09", "P.K.": 6392, "Tipología": "Anc", "Tipologia": "-",
         "Tipo de Suporte": "HEA 280 G.", "Maciços": "Poste parede",
         "Equipamentos associados": "Ancoraem coneção Sto Ov.", "Configuraciones": "C.F.21"}),
    # Fila 12: el aislador escrito en la funcion del apoyo, el maciço M1 y la marquesina.
    row({"SUPORTE": "3711CCA113", "P.K.": 6179.2, "Tipología": "Aislad.", "Tipologia": "Rígida",
         "Tipo de Suporte": "Marquise", "Maciços": "M1", "Desalinhamentos (cm)": "+4",
         "Alt. FC (m)": 3.8}),
    # Fila 13: aguja PA30 y mensula doble; en la via 2, un KP y un '-' donde iria el apoyo.
    row({"SUPORTE": "3713CCA15", "P.K.": 6441.5, "Tipología": "PA30", "Tipologia": "Cons. Dob.",
         "Implantação (m)": "6,73", "Maciços": "Anc. Pavi."},
        {"SUPORTE": "-", "P.K.": 6376.9, "Consolas": "C3- C.L."}),
    # Filas 14 y 15: un apoyo de la zona sin renumerar, que solo trae su codigo (y el
    # 'fuera del plano' de una columna que no se lee), y su fila intermedia, solo relleno.
    row({"SUPORTE": "3713CCA17"}, **{"c%d" % JUL_2026: "fuera del plano"}),
    row(),
    # Fila 16: en blanco en las columnas de via; solo una tabla suelta en la banda. Fin de
    # los datos.
    [None, None, "MEDIÇÃO JJ"] + [None] * (len(HEADER) - 3),
    # Filas 17 y 18: notas al pie. Una escribe un codigo y un KP donde iria un apoyo, y la
    # otra, un texto en la primera columna del apoyo de la via 2.
    [None if index not in (FIRST_ID_V2, column("SUPORTE", 2), column("P.K.", 2)) else
     (2527.9 if index == column("P.K.", 2) else "3703CCA36") for index in range(len(HEADER))],
    ["Cambios principales de la rev. 10"] + [None] * (len(HEADER) - 1),
]

# La hoja de la bretelle: titulo, cabecera en la fila 4 y, debajo, la subcabecera que dice
# la via de cada columna. La cabecera del KP dice '(vía 2)' para las dos: manda la subcabecera.
BRETELLE_HEADER = ["SUPORTE", None, "Vão (m)", None, "P.K. \n(vía 2)", None, "Tipología", None,
                   "Tipologia", None, "Tipo de Suporte", None, "Alt. FC (m)", None]
BRETELLE = [
    ["Linha Rubi"] + [None] * 13,
    ["Caderno de Montagem"] + [None] * 13,
    ["Bretelle 6+225"] + [None] * 13,
    BRETELLE_HEADER,
    ["VIA 1", "VIA 2"] * 7,
    ["3711CCA119_", "3711CCA120_", None, None, 6205.91, 6210.11, "S", "S", "Rígida", "Rígida",
     None, None, None, None],
    [None, None, 6.8, 6.8] + [None] * 10,
    ["3711CCA121", "3711CCA122", None, None, 6212.71, 6216.91, "S / PA30", "S / PA30",
     "Rígida", "Rígida", "C860-B", "C860-B", 3.8, 3.8],
    [None, None, 4.4, 4.4] + [None] * 10,
    # Un apoyo que solo esta en la via 1: en la via 2 queda un KP sin apoyo.
    ["3711CCA125.BIS", None, None, None, 6220.71, 6224.91, "SE", "SE", "Rígida", "Rígida",
     "C860-B", None, 3.8, 3.8],
]

# La hoja de UNA via ('VIA 1'): titulo, cabecera en la fila 4 sin marca de via, los hitos
# en espanol y la segunda mensula en las columnas SIN cabecera pegadas a la derecha de
# 'Consolas', 'Alt. FC' y 'Desalinhamentos' (y el segundo equipo, a la de 'Equipamentos').
# Es la que manda sobre el Sinóptico (ROWS), que la completa.
VIA_HEADER = ["SUPORTE", "Vão (m)", "P.K.\n(vía 1)", "P.K.\nAbs.vía 1", "Coord. X", "Tipología",
              "Implantação (m)", "Tipologia", "Tipo de Suporte", "Maciços", "Consolas", None,
              "L tubos consola (m)", "Alt. FC (m)", None, "Desalinhamentos", None, "Seccionamento",
              "Equipamentos associados ", None, None]
VIA_NAMES = [" ".join(str(name).split()) if name is not None else None for name in VIA_HEADER]


def via(**cells):
    """Una fila de la hoja de via. 'x2' es la gemela de la columna 'x'."""
    keys = {"id": "SUPORTE", "vao": "Vão (m)", "kp": "P.K. (vía 1)", "abs": "P.K. Abs.vía 1",
            "x": "Coord. X", "fun": "Tipología", "imp": "Implantação (m)", "sys": "Tipologia",
            "pole": "Tipo de Suporte", "mac": "Maciços", "con": "Consolas", "alt": "Alt. FC (m)",
            "des": "Desalinhamentos", "sec": "Seccionamento", "eq": "Equipamentos associados"}
    values = [None] * len(VIA_HEADER)
    for key, value in cells.items():
        twin = key.endswith("2")
        values[VIA_NAMES.index(keys[key.rstrip("2")]) + (1 if twin else 0)] = value
    return values


VIA_ROWS = [
    ["Linha Rubi"], ["Caderno de Montagem"], ["VIA 1"],
    VIA_HEADER,
    via(id="Existente", kp="??"),
    via(id="1-00.01", kp=-2.84, sys="Rígida", alt=3.8),
    via(id="Fin Anden Casa Da Música (00+040)", vao=6.2),
    # Fila 8: otro poste que en el Sinóptico (C860-A), sin el 'Seccionamento' que alli trae
    # la fila intermedia, y con la altura de la SEGUNDA mensula, que el Sinóptico no tiene.
    via(id="3504CCA19", kp=1061, abs=33744.6, x=-41642.72, fun="SE", sys="Rígida", pole="C859-A",
        des=-12, alt=3.8, des2="+20", alt2=3.9),
    via(vao=10),
    via(id="3505CCA01A", kp=1120.2, fun="Elev.", sys="Consola", pole="Pendulo 140", mac="Anc. Sot.",
        imp="1,5", des="+10", alt=3.83, con="B. Fun."),
    via(vao=10.6, eq="Disp. Transic.", sec="Transição Catenaria"),
    # Fila 12: dos mensulas y dos equipos, los segundos en las gemelas.
    via(id="3505CCA07", kp=1146.96, vao=6.3, fun="S/E A/S", sys="Funicular", pole="-",
        mac="Fixaçao Muro", des="+20", alt=3.8, con="Delta", con2="B.Fun.", des2="+10", alt2=3.84,
        eq="Ancoraem 2HC", eq2="Caix. Imped."),
    # Fila 13: el mismo apoyo que 3713CCA09 del Sinóptico (mismo KP), con el nombre de antes
    # de renumerar la zona.
    via(id="3713CCA10", kp=6392, fun="A/S", sys="-", pole="HEA 280 G.", mac="Poste parede",
        eq="Ancoraem coneção Sto Ov."),
    # Fila 14: el mismo nombre que en el Sinóptico y otro KP.
    via(id="3711CCA113", kp=6180, fun="Aislad.", sys="Rígida", pole="Marquise", mac="M1",
        des="+4", alt=3.8),
    # Fila 15: un apoyo que el Sinóptico no tiene.
    via(id="3713CCA99", kp=6500, sys="Rígida"),
]

CATALOG = {
    "Sectioning": {"S/A": "S/A", "A/S": "A/S", "MP": "MP", "A": "A", "P30": "P30", "P50": "P50",
                   "P80": "P80", "OVERLAP": "OVERLAP", "TRANSITION": "TRANSITION"},
    "Anchorage": {"AN1CW": "An1CW", "AN2CW": "An2CW", "ANCONN": "AnCONN"},
    "DisconnectorFunction": {"SECT-I": "SECT-I", "SECTCONNBOX": "SectConnBox", "IMPBOX": "ImpBox",
                             "TRANSDEV": "TransDev", "SECT-I-1CW": "SECT-I-1CW", "SECT-I-2CW": "SECT-I-2CW"},
    "SupportType": {"OCR SUPPORT": "OCR SUPPORT", "FLEXIBLE": "FLEXIBLE",
                    "SINGLE CANTILEVER": "SINGLE CANTILEVER", "DOUBLE CANTILEVER": "DOUBLE CANTILEVER"},
    "PoleType": {"C860-A": "C860-A", "C859-A": "C859-A", "C860-B": "C860-B", "PENDULO 140": "Pendulo 140", "HEA 280 G.": "HEA 280 G.",
                 "CANOPY": "CANOPY"},
    "Foundation": {"M1": "M1", "UNDERGROUND ANCHOR": "UNDERGROUND ANCHOR", "WALL FIXING": "WALL FIXING",
                   "WALL POLE": "WALL POLE", "PAVEMENT ANCHOR": "PAVEMENT ANCHOR"},
    "AssemblyConfiguration": {"C.C.2": "C.C.2", "C.F.21": "C.F.21"},
    "CantileverType": {"OCR": "OCR", "UNKNOWN": "UNKNOWN", "DELTA": "DELTA",
                       "FLEXIBLE ARM": "FLEXIBLE ARM"},
}

CFG = dict(ALIASES, defaults={"profile_status": "DEFINITIVE"}, lov_catalog=CATALOG)


def read(block, name, sheet=None):
    master = bpm.Master()
    sheet = sheet or FakeSheet("Sinóptico", ROWS)
    profiles, cantilevers = bpm.read_synoptic_track(
        sheet, "RUBI", {"block": block, "name": name}, CFG, master)
    return master, profiles, cantilevers


def layout_of(rows, title="Sinóptico"):
    master = bpm.Master()
    return synoptic.synoptic_layout(FakeSheet(title, rows), "RUBI", CFG, master), master


class PlanoDeLaHoja(unittest.TestCase):
    """Cada columna es de la via que dice su cabecera, y se resuelve por nombre."""

    def setUp(self):
        self.layout, self.master = layout_of(ROWS)

    def test_la_cabecera_se_localiza_por_contenido_y_hay_dos_vias(self):
        self.assertEqual(self.layout.header_index, 1)
        self.assertEqual(self.layout.first_data, 2)
        self.assertEqual(list(self.layout.blocks), ["VIA 1", "VIA 2"])
        self.assertEqual(self.master.unknown, {})

    def test_el_sufijo_de_la_cabecera_dice_la_via(self):
        one, two = self.layout.blocks["VIA 1"], self.layout.blocks["VIA 2"]
        self.assertEqual(one.single["PROFILE_ID"], [column("SUPORTE", 1)])
        self.assertEqual(one.single["KP"], [column("P.K.", 1)])       # 'P.K. (via 1)'
        self.assertEqual(two.single["KP"], [column("P.K.", 2)])
        self.assertEqual(one.single["FOUNDATION"], [column("Maciços", 1)])
        self.assertEqual(two.single["FOUNDATION"], [column("Maciços", 2)])
        # Con tilde y sin tilde son dos columnas distintas.
        self.assertEqual(one.single["SUPPORT_TYPE"], [column("Tipologia", 1)])
        self.assertEqual(one.multi["SECTIONING"],
                         [column("Tipología", 1), column("Seccionamento", 1)])

    def test_la_cabecera_repetida_sin_via_se_reparte_por_posicion(self):
        first = NAMES.index("Vão (m)")
        self.assertEqual(self.layout.blocks["VIA 1"].single["SPAN"], [first])
        self.assertEqual(self.layout.blocks["VIA 2"].single["SPAN"], [first + 1])

    def test_el_apoyo_de_la_via_2_esta_dos_veces_y_son_la_misma_columna(self):
        self.assertEqual(self.layout.blocks["VIA 2"].single["PROFILE_ID"],
                         [FIRST_ID_V2, column("SUPORTE", 2)])

    def test_la_segunda_mensula_y_el_segundo_equipo_tienen_su_cabecera(self):
        one, two = self.layout.blocks["VIA 1"], self.layout.blocks["VIA 2"]
        self.assertEqual(one.second["CANTILEVER_TYPE"], [column("Consolas 2", 1)])
        self.assertEqual(two.second["STAGGER"], [column("Desalinhamentos2 (cm)", 2)])
        self.assertNotIn("CW_HEIGHT", one.second)     # no hay una segunda 'Alt. FC'
        self.assertEqual(one.multi["EQUIPMENT"], [column("Equipamentos associados", 1),
                                                  column("Equipamentos associados 2", 1)])
        self.assertEqual(two.multi["EQUIPMENT"], [column("Equipamentos associados", 2),
                                                  column("Equipamentos associados 2", 2)])

    def test_lo_que_no_es_de_ninguna_via_vale_para_las_dos(self):
        compartidas = dict(self.layout.shared)
        self.assertEqual(compartidas["Activo"], column("Activo"))
        # 'Tipología' sin sufijo NO es la funcion del apoyo.
        self.assertEqual(compartidas["Tipología"], SHARED_TYPOLOGY)
        self.assertNotIn(SHARED_TYPOLOGY, self.layout.blocks["VIA 1"].multi["SECTIONING"])
        self.assertEqual(compartidas["1"], BAND)
        # Una columna sin cabecera detras de un campo no es de ese campo.
        self.assertEqual(compartidas["sin cabecera I"], LOOSE)

    def test_una_columna_sin_cabecera_continua_la_de_su_izquierda(self):
        etiquetas = dict(self.layout.blocks["VIA 1"].unmapped)
        self.assertEqual(etiquetas["Cabos feeder longitudinais V1"],
                         column("Cabos feeder longitudinais", 1))
        self.assertEqual(etiquetas["Cabos feeder longitudinais V1 (AT)"], FEEDER_2)

    def test_lo_ignorado_por_declaracion_no_es_de_nadie(self):
        leidas = {index for block in self.layout.blocks.values() for index in block.columns}
        leidas |= {index for _, index in self.layout.shared}
        for index in (1, 3, JUL_2026, JUL_2026 + 1, NAMES.index("Tipologia (traçado Jul.2026) V1"),
                      SIDE_TABLE, SIDE_TABLE + 1):
            self.assertNotIn(index, leidas, HEADER[index])

    def test_los_datos_acaban_en_la_primera_fila_sin_nada_de_ninguna_via(self):
        # La fila 15 solo trae el relleno 'VIA 1' / 'VIA 2' y no es el final; la 16 solo
        # trae una tabla en la banda, que no es de ninguna via, y si lo es.
        self.assertEqual(self.layout.data_end, 15)

    def test_una_cabecera_desconocida_no_pasa_en_silencio(self):
        rows = [list(r) for r in ROWS]
        rows[1][column("Activo")] = "Columna nueva"
        rows[1][column("Maciços", 2)] = "Columna rara V2"
        _, master = layout_of(rows)
        self.assertEqual(sorted(u["valor"] for u in master.unknown.values()),
                         ["columna nueva", "columna rara"])

    def test_un_campo_sin_via_que_no_esta_una_vez_por_via_no_se_adivina(self):
        rows = [list(r) for r in ROWS]
        rows[1][NAMES.index("Vão (m)") + 1] = None       # queda un solo 'Vão (m)'
        _, master = layout_of(rows)
        self.assertEqual([(u["tipo"], u["valor"]) for u in master.unknown.values()],
                         [("cabecera sinoptico que no dice su via", "vão (m)")])

    def test_un_ep_sin_format_sigue_por_el_camino_de_las_hojas_hr_track(self):
        self.assertFalse(synoptic.is_synoptic({"file": "EP6.xlsm"}))
        self.assertFalse(synoptic.is_synoptic(None))
        self.assertTrue(synoptic.is_synoptic({"format": "synoptic"}))


class LaSubcabecera(unittest.TestCase):
    """La hoja de la bretelle: la via de cada columna la dice la fila de debajo."""

    @classmethod
    def setUpClass(cls):
        cls.layout, cls.unknown = layout_of(BRETELLE, "Bret. 6+225")
        cls.one, _, _ = read("VIA 1", "BRETELLE 6+225 TRACK 1", FakeSheet("Bret. 6+225", BRETELLE))
        cls.two, _, _ = read("VIA 2", "BRETELLE 6+225 TRACK 2", FakeSheet("Bret. 6+225", BRETELLE))

    def test_la_cabecera_vale_para_sus_dos_columnas(self):
        self.assertEqual((self.layout.header_index, self.layout.first_data), (3, 5))
        self.assertEqual(self.layout.blocks["VIA 1"].single["PROFILE_ID"], [0])
        self.assertEqual(self.layout.blocks["VIA 2"].single["PROFILE_ID"], [1])
        # 'P.K. (vía 2)' encima de las dos: manda la subcabecera.
        self.assertEqual(self.layout.blocks["VIA 1"].single["KP"], [4])
        self.assertEqual(self.layout.blocks["VIA 2"].single["KP"], [5])
        self.assertEqual(self.unknown.unknown, {})

    def test_cada_via_tiene_sus_apoyos_literales_y_su_kp(self):
        self.assertEqual([(p["PROFILE_ID"], p["KP"]) for p in self.one.profiles],
                         [("3711CCA119_", Decimal("6205.910")), ("3711CCA121", Decimal("6212.710")),
                          ("3711CCA125.BIS", Decimal("6220.710"))])
        self.assertEqual([(p["PROFILE_ID"], p["KP"]) for p in self.two.profiles],
                         [("3711CCA120_", Decimal("6210.110")), ("3711CCA122", Decimal("6216.910"))])
        self.assertTrue(all(p["HOJA_ORIGEN"] == "Bret. 6+225 / VIA 2" for p in self.two.profiles))

    def test_la_suspension_con_aguja_en_una_celda_es_la_aguja(self):
        perfil = {p["PROFILE_ID"]: p for p in self.one.profiles}["3711CCA121"]
        self.assertEqual(perfil["SECTIONING"], "P30")                   # 'S / PA30'
        self.assertEqual(perfil["POLE_TYPE"], "C860-B")
        self.assertEqual(perfil["SPAN"], Decimal("4.400"))

    def test_un_kp_sin_apoyo_no_se_suma_al_apoyo_anterior(self):
        anterior = {p["PROFILE_ID"]: p for p in self.two.profiles}["3711CCA122"]
        self.assertEqual(anterior["SECTIONING"], "P30")                 # sin el 'SE' de la fila 10
        self.assertIn(("fila con KP y sin apoyo: no se suma al apoyo anterior", 10),
                      {(d["motivo"], d["fila"]) for d in self.two.discarded})


class KilometracionDelSinoptico(unittest.TestCase):

    def test_numero_coma_y_kilometracion_con_mas(self):
        self.assertEqual(synoptic.parse_kp(1061), Decimal("1061"))
        self.assertEqual(synoptic.parse_kp(1059.2), Decimal("1059.2"))
        self.assertEqual(synoptic.parse_kp("1+118,8"), Decimal("1118.8"))
        self.assertEqual(synoptic.parse_kp("6 392,5"), Decimal("6392.5"))

    def test_el_signo_de_la_kilometracion_es_el_del_kp_entero(self):
        # El signo va delante de los kilometros pero es de todo el KP: se aplica a
        # km * 1000 + m. Con '-0' no habia signo que heredar (salia +10) y con '-1' los
        # metros se sumaban en vez de restarse (salia -800).
        self.assertEqual(synoptic.parse_kp("-0+010"), Decimal("-10"))
        self.assertEqual(synoptic.parse_kp("-1+200"), Decimal("-1200"))
        self.assertEqual(synoptic.parse_kp("-0+077,96"), Decimal("-77.96"))
        self.assertEqual(synoptic.parse_kp(" -0 + 077,96 "), Decimal("-77.96"))
        self.assertEqual(synoptic.parse_kp("-12+005.5"), Decimal("-12005.5"))

    def test_la_kilometracion_positiva_no_cambia(self):
        self.assertEqual(synoptic.parse_kp("0+010"), Decimal("10"))
        self.assertEqual(synoptic.parse_kp("+0+010"), Decimal("10"))
        self.assertEqual(synoptic.parse_kp("1+200"), Decimal("1200"))
        self.assertEqual(synoptic.parse_kp("+1+200"), Decimal("1200"))
        self.assertEqual(synoptic.parse_kp("30+719"), Decimal("30719"))
        self.assertEqual(synoptic.parse_kp("0+000"), Decimal("0"))
        self.assertFalse(synoptic.parse_kp("-0+000").is_signed())
        self.assertEqual(synoptic.parse_kp("-10"), Decimal("-10"))
        self.assertEqual(synoptic.parse_kp("-77,96"), Decimal("-77.96"))

    def test_el_kp_de_una_hoja_hr_track_hereda_el_signo(self):
        self.assertEqual(bpm.track_kp("-0+010"), Decimal("-10"))
        self.assertEqual(bpm.track_kp("30+719"), Decimal("30719"))

    def test_los_huecos_no_son_un_kp(self):
        for value in (None, "", "??", "-", "abc", True):
            self.assertIsNone(synoptic.parse_kp(value), repr(value))


class LecturaDeUnaVia(unittest.TestCase):
    """La via 1: las columnas ' V1'."""

    @classmethod
    def setUpClass(cls):
        cls.master, cls.profiles, cls.cantilevers = read("VIA 1", "TRACK 1")
        cls.by_id = {p["PROFILE_ID"]: p for p in cls.master.profiles}

    def test_solo_los_apoyos_son_perfiles_y_en_su_orden(self):
        self.assertEqual([p["PROFILE_ID"] for p in self.master.profiles],
                         ["1-00.01", "3504CCA19", "3505CCA01A", "3505CCA07", "3713CCA09",
                          "3711CCA113", "3713CCA15", "3713CCA17"])
        self.assertEqual([p["ORDEN"] for p in self.master.profiles], [1, 2, 3, 4, 5, 6, 7, 8])
        self.assertTrue(all(p["VIA"] == "TRACK 1" for p in self.master.profiles))
        self.assertTrue(all(p["HOJA_ORIGEN"] == "Sinóptico / VIA 1" for p in self.master.profiles))

    def test_el_hito_se_descarta_con_motivo(self):
        motivos = {(d["motivo"], d["fila"]) for d in self.master.discarded}
        self.assertIn(("hito de trazado, no es un apoyo", 3), motivos)
        self.assertIn(("hito de trazado, no es un apoyo", 5), motivos)

    def test_el_hito_ocupa_la_fila_intermedia_y_su_vano_es_del_apoyo_anterior(self):
        self.assertEqual(self.by_id["1-00.01"]["SPAN"], Decimal("6.200"))

    def test_el_vano_de_la_fila_intermedia_manda_sobre_el_de_la_fila_del_apoyo(self):
        # 3504CCA19 trae un 4 en su fila y un 10 en la intermedia; 3505CCA07 solo el suyo.
        self.assertEqual(self.by_id["3504CCA19"]["SPAN"], Decimal("10.000"))
        self.assertEqual(self.by_id["3505CCA07"]["SPAN"], Decimal("6.300"))

    def test_los_hitos_en_portugues_tambien_lo_son(self):
        for texto in ("Existente", "Iníc. Est. Campo Alegre (00+966)", "Fim Ponte (02+059)",
                      "Cruzamento Rodoviário", "Fin. Ponte (02+059)", "Cruce Vial"):
            self.assertTrue(synoptic.is_landmark(texto, CFG), texto)
        self.assertFalse(synoptic.is_landmark("3713CCA17", CFG))

    def test_un_apoyo_que_solo_trae_su_codigo_entra_sin_kp_y_desactivado(self):
        # Los de la zona 3713, pendiente de renumerar: no son un hito ni una nota al pie.
        perfil = self.by_id["3713CCA17"]
        self.assertIsNone(perfil["KP"])
        self.assertEqual((perfil["ENABLED"], perfil["REVISAR"]), ("NO", "SI"))

    def test_lo_ignorado_no_deja_rastro(self):
        # '(traçado Jul.2026)' y la tabla de la derecha: ni dato, ni NO_MAPEADO, ni descarte.
        todo = repr(self.master.profiles) + repr(self.master.unmapped) + repr(self.master.discarded)
        for texto in ("fuera del plano", "TRAJECTOS", "Origem - destino"):
            self.assertNotIn(texto, todo)

    def test_las_columnas_sin_campo_se_conservan_una_vez_por_apoyo(self):
        anotaciones = [(u["COLUMNA"], u["VALOR"], u["FILA_ORIGEN"]) for u in self.master.unmapped
                       if u["PROFILE_ID"] == "3504CCA19"]
        for esperado in (("P.K. Abs.via 1", "33744.6", 6),
                         ("Cabos feeder longitudinais V1", "2 FA", 6),
                         ("Cabos feeder longitudinais V1 (AT)", "3 N", 6),
                         ("Activo", "3504", 6), ("Tipología", "TIPO R", 6), ("1", "CAT D", 6),
                         ("sin cabecera I", "13.08", 6)):
            self.assertIn(esperado, anotaciones)
        # 'Activo' esta tambien en la fila intermedia: no se repite.
        self.assertEqual(len([a for a in anotaciones if a[0] == "Activo"]), 1)

    def test_el_kp_negativo_es_un_kp(self):
        # Los dos apoyos de Casa da Musica estan antes del origen de la kilometracion.
        perfil = self.by_id["1-00.01"]
        self.assertEqual(perfil["KP"], Decimal("-2.840"))
        self.assertEqual((perfil["ENABLED"], perfil["REVISAR"]), ("SI", "NO"))
        self.assertNotIn(4, {d["fila"] for d in self.master.discarded if d["motivo"].startswith("KP")})

    def test_el_vano_y_el_seccionamento_vienen_de_la_fila_intermedia(self):
        perfil = self.by_id["3504CCA19"]
        self.assertEqual(perfil["SPAN"], Decimal("10.000"))
        self.assertEqual(perfil["SECTIONING"], "S/A|OVERLAP")     # SE + 'Seccionamento'
        self.assertEqual(perfil["SUPPORT_TYPE"], "OCR SUPPORT")   # Rígida
        self.assertEqual(perfil["POLE_TYPE"], "C860-A")

    def test_la_longitud_del_canton_no_es_un_codigo_y_se_conserva(self):
        anotaciones = [(u["COLUMNA"], u["VALOR"]) for u in self.master.unmapped
                       if u["PROFILE_ID"] == "3504CCA19"]
        self.assertIn(("Seccionamento V1", "Cantón 290 m"), anotaciones)

    def test_la_catenaria_de_mensula_y_la_transicion(self):
        perfil = self.by_id["3505CCA01A"]
        self.assertEqual(perfil["KP"], Decimal("1120.200"))
        self.assertEqual(perfil["SUPPORT_TYPE"], "SINGLE CANTILEVER")   # Consola
        self.assertEqual(perfil["SECTIONING"], "S/A|TRANSITION")        # Elev. + Transição
        self.assertEqual(perfil["SECTIONING_FEEDING"], "TransDev")      # Disp. Transic.
        self.assertEqual(perfil["POLE_TYPE"], "Pendulo 140")
        self.assertEqual(perfil["FOUNDATION"], "UNDERGROUND ANCHOR")    # Anc. Sot.
        self.assertEqual(perfil["RAIL_POLE_DISTANCE"], Decimal("1500"))  # 1,5 m -> mm
        self.assertEqual(perfil["ASSEMBLY_CONFIGURATION"], "")           # el 0 es hueco

    def test_la_funcion_doble_se_parte_y_los_equipos_se_reparten(self):
        perfil = self.by_id["3505CCA07"]
        self.assertEqual(perfil["SECTIONING"], "S/A|A/S")               # 'S/E A/S'
        self.assertEqual(perfil["SUPPORT_TYPE"], "FLEXIBLE")            # Funicular
        self.assertEqual(perfil["ANCHORAGE"], "An2CW")                  # Ancoraem 2HC
        self.assertEqual(perfil["SECTIONING_FEEDING"], "ImpBox")        # Caix. Imped.
        self.assertEqual(perfil["FOUNDATION"], "WALL FIXING")           # Fixaçao Muro
        self.assertEqual(perfil["ASSEMBLY_CONFIGURATION"], "C.C.2")     # errata C.C,2
        self.assertEqual(perfil["POLE_TYPE"], "")                       # '-'
        self.assertEqual(perfil["REVISAR"], "NO")

    def test_la_errata_anc_sale_vacia_y_anotada(self):
        perfil = self.by_id["3713CCA09"]
        self.assertEqual(perfil["SECTIONING"], "")
        self.assertEqual(perfil["REVISAR"], "SI")
        self.assertEqual(perfil["ANCHORAGE"], "AnCONN")
        self.assertEqual(perfil["ASSEMBLY_CONFIGURATION"], "C.F.21")
        self.assertEqual(perfil["FOUNDATION"], "WALL POLE")
        self.assertTrue(any("'Anc'" in d["motivo"] and d["fila"] == 11 for d in self.master.discarded))

    def test_el_aislador_va_a_la_alimentacion_y_m1_es_un_macizo(self):
        perfil = self.by_id["3711CCA113"]
        self.assertEqual(perfil["SECTIONING"], "")
        self.assertEqual(perfil["SECTIONING_FEEDING"], "SECT-I")        # Aislad.
        self.assertEqual(perfil["POLE_TYPE"], "CANOPY")                 # Marquise
        self.assertEqual(perfil["FOUNDATION"], "M1")                    # no es una subcabecera

    def test_las_agujas_y_la_implantacion(self):
        perfil = self.by_id["3713CCA15"]
        self.assertEqual(perfil["SECTIONING"], "P30")                   # PA30
        self.assertEqual(perfil["SUPPORT_TYPE"], "DOUBLE CANTILEVER")   # Cons. Dob.
        self.assertEqual(perfil["RAIL_POLE_DISTANCE"], Decimal("6730"))
        self.assertEqual(perfil["FOUNDATION"], "PAVEMENT ANCHOR")

    def test_las_dos_mensulas_y_el_fallback_de_la_rigida(self):
        por_perfil = {}
        for c in self.master.cantilevers:
            por_perfil.setdefault(c["PROFILE_ID"], []).append(
                (c["SLOT"], c["CANTILEVER_TYPE"], c["STAGGER"], c["CW_HEIGHT"]))
        # Rigida sin consola: la mensula es OCR, deducida del soporte, y 'Desalinhamentos2'
        # es la segunda. No hay una segunda 'Alt. FC': su altura llega vacia.
        self.assertEqual(por_perfil["3504CCA19"],
                         [(1, "OCR", Decimal("-12"), Decimal("3.800")), (2, "OCR", Decimal("20"), None)])
        # Flexible con dos consolas escritas.
        self.assertEqual(por_perfil["3505CCA07"],
                         [(1, "DELTA", Decimal("20"), Decimal("3.800")),
                          (2, "FLEXIBLE ARM", Decimal("10"), None)])
        self.assertEqual(por_perfil["3505CCA01A"], [(1, "FLEXIBLE ARM", Decimal("10"), Decimal("3.830"))])
        # El apoyo de KP negativo no carga mensula: solo traia la altura, sin evidencia.
        self.assertNotIn("1-00.01", por_perfil)
        self.assertEqual(self.cantilevers, len(self.master.cantilevers))

    def test_ningun_codigo_del_maestro_lleva_una_grafia_del_origen(self):
        grafias = {synoptic.squash(k).upper()
                   for table in CFG["synoptic"]["translations"].values() for k, v in table.items()
                   if synoptic.squash(k).upper() != synoptic.squash(v or "").upper()}
        emitidos = set()
        for p in self.master.profiles:
            for field in bpm.PROFILE_LOV_FIELDS:
                emitidos.update(str(p[field] or "").split(bpm.LOV_SEPARATOR))
        for c in self.master.cantilevers:
            emitidos.add(c["CANTILEVER_TYPE"])
        self.assertEqual({e.upper() for e in emitidos if e} & grafias, set())

    def test_nada_queda_sin_reconocer(self):
        self.assertEqual(self.master.unknown, {})


class LecturaDeLaOtraVia(unittest.TestCase):
    """La via 2: las columnas ' V2', intercaladas con las de la via 1."""

    @classmethod
    def setUpClass(cls):
        cls.master, _, _ = read("VIA 2", "TRACK 2")
        cls.by_id = {p["PROFILE_ID"]: p for p in cls.master.profiles}

    def test_los_mismos_apoyos_con_su_propio_kp(self):
        self.assertEqual([p["PROFILE_ID"] for p in self.master.profiles],
                         ["1-00.02", "3504CCA20", "3505CCA02", "3505CCA08"])
        self.assertEqual(self.by_id["3505CCA02"]["KP"], Decimal("1118.800"))   # '1+118,8'

    def test_la_segunda_mensula_es_la_de_su_via(self):
        mensulas = [(c["SLOT"], c["CANTILEVER_TYPE"], c["STAGGER"]) for c in self.master.cantilevers
                    if c["PROFILE_ID"] == "3505CCA08"]
        self.assertEqual(mensulas, [(1, "DELTA", Decimal("20")), (2, "FLEXIBLE ARM", Decimal("10"))])

    def test_un_guion_donde_iria_el_codigo_no_es_un_apoyo_ni_una_fila_intermedia(self):
        self.assertNotIn("-", self.by_id)
        self.assertEqual(self.by_id["3505CCA08"]["SPAN"], None)
        # Trae KP: no es la fila intermedia de 3505CCA08, y su consola no se le suma.
        self.assertEqual([c["CANTILEVER_TYPE"] for c in self.master.cantilevers
                          if c["PROFILE_ID"] == "3505CCA08"], ["DELTA", "FLEXIBLE ARM"])
        self.assertIn(("fila con KP y sin apoyo: no se suma al apoyo anterior", 13),
                      {(d["motivo"], d["fila"]) for d in self.master.discarded})

    def test_la_nota_al_pie_no_es_un_apoyo_aunque_traiga_codigo_y_kp(self):
        self.assertNotIn("3703CCA36", self.by_id)
        notas = {(d["motivo"], d["fila"], d["detalle"]) for d in self.master.discarded}
        self.assertIn(("fila fuera del bloque de datos", 17, "3703CCA36"), notas)
        # La nota escrita solo en la primera columna del apoyo de la via 2 (la A).
        self.assertIn(("fila fuera del bloque de datos", 18, "Cambios principales de la rev. 10"),
                      notas)


class FakeWorkbook:
    def __init__(self, *sheets):
        self.worksheets = list(sheets)


MAIN_TRACK = {"sheet": "VIA 1", "block": "VIA 1", "name": "TRACK 1", "station": "A",
              "complement": {"sheet": "Sinóptico", "block": "VIA 1"}}


def read_workbook(declarations, *sheets):
    by_sheet = collections.defaultdict(list)
    for decl in declarations:
        by_sheet[decl["sheet"]].append(decl)
    master = bpm.Master()
    counts = bpm.read_synoptic_workbook(FakeWorkbook(*sheets), "RUBI", {"tracks": declarations},
                                        by_sheet, CFG, master)
    return master, counts


class LaHojaDeUnaVia(unittest.TestCase):
    """La hoja 'VIA 1': entera de la via que declara topology.yml, con sus gemelas."""

    def setUp(self):
        self.master = bpm.Master()
        self.layout = synoptic.synoptic_layout(FakeSheet("VIA 1", VIA_ROWS), "RUBI", CFG,
                                               self.master, only="VIA 1")
        self.block = self.layout.blocks["VIA 1"]

    def test_la_hoja_entera_es_de_la_via_declarada(self):
        self.assertEqual((self.layout.header_index, list(self.layout.blocks)), (3, ["VIA 1"]))
        self.assertEqual(self.block.single["PROFILE_ID"], [0])
        self.assertEqual(self.block.single["KP"], [VIA_NAMES.index("P.K. (vía 1)")])
        self.assertEqual(self.block.single["STAGGER"], [VIA_NAMES.index("Desalinhamentos")])
        self.assertEqual(self.master.unknown, {})

    def test_la_marca_de_otra_via_en_una_cabecera_no_reparte_nada(self):
        # La hoja 'VIA 2' real rotula 'P.K. Abs.vía 1': sigue siendo de la via de la hoja.
        master = bpm.Master()
        layout = synoptic.synoptic_layout(FakeSheet("VIA 2", VIA_ROWS), "RUBI", CFG, master,
                                          only="VIA 2")
        self.assertEqual(list(layout.blocks), ["VIA 2"])
        self.assertIn("P.K. Abs.vía 1", dict(layout.blocks["VIA 2"].unmapped))
        self.assertEqual(master.unknown, {})

    def test_la_gemela_es_la_columna_sin_cabecera_pegada_a_la_derecha(self):
        self.assertEqual(self.block.second["CANTILEVER_TYPE"], [VIA_NAMES.index("Consolas") + 1])
        self.assertEqual(self.block.second["CW_HEIGHT"], [VIA_NAMES.index("Alt. FC (m)") + 1])
        self.assertEqual(self.block.second["STAGGER"], [VIA_NAMES.index("Desalinhamentos") + 1])
        equipo = VIA_NAMES.index("Equipamentos associados")
        self.assertEqual(self.block.multi["EQUIPMENT"], [equipo, equipo + 1])
        # Solo UNA: la siguiente sin cabecera ya no es de ningun campo.
        self.assertEqual(dict(self.layout.shared), {"sin cabecera U": equipo + 2})

    def test_sin_la_via_declarada_la_hoja_no_se_adivina(self):
        master = bpm.Master()
        self.assertIsNone(synoptic.synoptic_layout(FakeSheet("VIA 1", VIA_ROWS), "RUBI", CFG, master))
        self.assertTrue(master.unknown)


class MandaLaHojaDeLaVia(unittest.TestCase):
    """La via general sale de dos hojas: la suya manda y el Sinóptico pone lo que no trae."""

    @classmethod
    def setUpClass(cls):
        cls.master, cls.counts = read_workbook(
            [MAIN_TRACK], FakeSheet("VIA 1", VIA_ROWS), FakeSheet("Sinóptico", ROWS))
        cls.by_id = {p["PROFILE_ID"]: p for p in cls.master.profiles}
        cls.notes = [(d["motivo"], d["hoja"], d["fila"], d["detalle"]) for d in cls.master.discarded]

    def arms(self, code):
        return [(c["SLOT"], c["CANTILEVER_TYPE"], c["STAGGER"], c["CW_HEIGHT"])
                for c in self.master.cantilevers if c["PROFILE_ID"] == code]

    def test_los_apoyos_son_los_de_la_hoja_de_la_via_mas_los_que_solo_trae_el_sinoptico(self):
        self.assertEqual([(p["PROFILE_ID"], p["HOJA_ORIGEN"], p["FILA_ORIGEN"])
                          for p in self.master.profiles],
                         [("1-00.01", "VIA 1 / VIA 1", 6), ("3504CCA19", "VIA 1 / VIA 1", 8),
                          ("3505CCA01A", "VIA 1 / VIA 1", 10), ("3505CCA07", "VIA 1 / VIA 1", 12),
                          ("3713CCA10", "VIA 1 / VIA 1", 13), ("3711CCA113", "VIA 1 / VIA 1", 14),
                          # Los dos que la hoja de la via no tiene, detras del ultimo casado.
                          ("3713CCA15", "Sinóptico / VIA 1", 13), ("3713CCA17", "Sinóptico / VIA 1", 14),
                          ("3713CCA99", "VIA 1 / VIA 1", 15)])
        self.assertEqual([t["HOJA_ORIGEN"] for t in self.master.tracks], ["VIA 1 / VIA 1"])
        self.assertEqual(self.master.unknown, {})

    def test_la_celda_de_la_hoja_de_la_via_manda_tambien_vacia(self):
        perfil = self.by_id["3504CCA19"]
        self.assertEqual(perfil["POLE_TYPE"], "C859-A")            # el Sinóptico dice C860-A
        self.assertEqual(perfil["SECTIONING"], "S/A")              # sin el OVERLAP del Sinóptico
        self.assertEqual(perfil["SPAN"], Decimal("10.000"))
        detalles = [n[3] for n in self.notes
                    if n[0] == "manda la hoja de la via: Sinóptico dice otra cosa" and n[2] == 8]
        self.assertIn("3504CCA19 POLE_TYPE: C859-A / C860-A (Sinóptico fila 6)", detalles)
        self.assertTrue(any(d.startswith("3504CCA19 SECTIONING: SE / ") for d in detalles), detalles)

    def test_la_segunda_mensula_trae_su_altura_de_la_gemela(self):
        self.assertEqual(self.arms("3504CCA19"), [(1, "OCR", Decimal("-12"), Decimal("3.800")),
                                                  (2, "OCR", Decimal("20"), Decimal("3.900"))])
        self.assertEqual(self.arms("3505CCA07"),
                         [(1, "DELTA", Decimal("20"), Decimal("3.800")),
                          (2, "FLEXIBLE ARM", Decimal("10"), Decimal("3.840"))])
        self.assertEqual(self.by_id["3505CCA07"]["ANCHORAGE"], "An2CW")
        self.assertEqual(self.by_id["3505CCA07"]["SECTIONING_FEEDING"], "ImpBox")   # el segundo equipo

    def test_lo_que_la_hoja_de_la_via_no_trae_sale_del_sinoptico(self):
        # 'Configuraciones' solo esta en el Sinóptico; tambien 'Activo' y las demas sin campo.
        self.assertEqual(self.by_id["3505CCA07"]["ASSEMBLY_CONFIGURATION"], "C.C.2")
        self.assertEqual(self.by_id["3713CCA10"]["ASSEMBLY_CONFIGURATION"], "C.F.21")
        anotaciones = {(u["COLUMNA"], u["VALOR"]) for u in self.master.unmapped
                       if u["PROFILE_ID"] == "3504CCA19"}
        self.assertLessEqual({("Activo", "3504"), ("Tipología", "TIPO R"),
                              ("Cabos feeder longitudinais V1", "2 FA")}, anotaciones)
        # Las que tiene la hoja de la via salen de ella, una vez.
        self.assertIn(("P.K. Abs.vía 1", "33744.6"), anotaciones)
        self.assertIn(("Coord. X", "-41642.72"), anotaciones)
        self.assertNotIn("P.K. Abs.via 1", {columna for columna, _ in anotaciones})

    def test_un_apoyo_renumerado_se_casa_por_su_kp_y_lleva_el_nombre_de_la_hoja_de_la_via(self):
        self.assertNotIn("3713CCA09", self.by_id)
        self.assertEqual(self.by_id["3713CCA10"]["ANCHORAGE"], "AnCONN")
        self.assertEqual(self.by_id["3713CCA10"]["SECTIONING"], "A/S")       # alli era la errata 'Anc'
        self.assertIn(("apoyo casado con Sinóptico por su KP: alli tiene otro nombre", "VIA 1 / VIA 1",
                       13, "3713CCA10 = 3713CCA09 (Sinóptico fila 11), KP 6392 / 6392"), self.notes)

    def test_el_mismo_nombre_con_otro_kp_es_el_mismo_apoyo_y_manda_el_kp_de_la_via(self):
        self.assertEqual(self.by_id["3711CCA113"]["KP"], Decimal("6180.000"))
        self.assertIn(("apoyo casado con Sinóptico por su nombre: alli tiene otro KP", "VIA 1 / VIA 1",
                       14, "3711CCA113 = 3711CCA113 (Sinóptico fila 12), KP 6180 / 6179.2"), self.notes)

    def test_lo_que_no_se_casa_se_ve_en_los_dos_sentidos(self):
        self.assertIn(("apoyo que no esta en Sinóptico: sin lo que solo trae esa hoja",
                       "VIA 1 / VIA 1", 15, "3713CCA99"), self.notes)
        self.assertIn(("apoyo que solo esta en Sinóptico: entra de alli", "Sinóptico / VIA 1", 13,
                       "3713CCA15"), self.notes)
        self.assertEqual(self.by_id["3713CCA15"]["SECTIONING"], "P30")
        # 3713CCA17 no trae KP: entra en su sitio y desactivado, como siempre.
        self.assertEqual(self.by_id["3713CCA17"]["ENABLED"], "NO")

    def test_los_hitos_son_los_de_la_hoja_de_la_via_y_no_se_repiten(self):
        hitos = [(n[1], n[2]) for n in self.notes if n[0] == "hito de trazado, no es un apoyo"]
        self.assertEqual(hitos, [("VIA 1 / VIA 1", 5), ("VIA 1 / VIA 1", 7)])
        self.assertEqual(self.by_id["1-00.01"]["SPAN"], Decimal("6.200"))

    def test_el_catalogo_cosecha_la_misma_lectura(self):
        cat = blm.Catalogue(ALIASES)
        leidas = blm.read_synoptic_catalog(
            [FakeSheet("VIA 1", VIA_ROWS), FakeSheet("Sinóptico", ROWS)], "RUBI",
            {"tracks": [MAIN_TRACK]}, ALIASES, cat)
        codigos = {(r["entity"], r["code"]) for r in cat.rows.values()}
        self.assertEqual(leidas, 1)
        self.assertIn(("PoleType", "C859-A"), codigos)                  # el de la hoja de la via
        self.assertNotIn(("PoleType", "C860-A"), codigos)               # el del Sinóptico, no
        self.assertIn(("AssemblyConfiguration", "C.C.2"), codigos)      # solo lo trae el Sinóptico
        self.assertNotIn(("Sectioning", "OVERLAP"), codigos)
        self.assertEqual(cat.unknown, {})


class HojasDelWorkbook(unittest.TestCase):
    """Cada hoja de un workbook sinoptico se declara: para leerla o para omitirla."""

    DECLARED = [
        {"sheet": "Sinóptico", "block": "VIA 1", "name": "TRACK 1", "station": "A"},
        {"sheet": "Bret. 6+225", "block": "VIA 2", "name": "BRETELLE 6+225 TRACK 2", "station": "A"},
        {"sheet": "VIA 1", "skip": "copia de la via 1 del Sinóptico"},
    ]

    def read(self, *sheets):
        return read_workbook(self.DECLARED, *sheets)

    def test_las_vias_de_las_dos_hojas_y_la_hoja_omitida_con_su_motivo(self):
        # La hoja 'VIA 1' lleva la cabecera de otro formato: omitida, ni se mira.
        master, (sheets, profiles, _) = self.read(
            FakeSheet("Sinóptico", ROWS), FakeSheet("VIA 1", [["SUPORTE", "Columna rara"]]),
            FakeSheet("Bret. 6+225", BRETELLE))
        self.assertEqual((sheets, profiles), (2, 8 + 2))
        self.assertEqual([(t["NOMBRE"], t["HOJA_ORIGEN"]) for t in master.tracks],
                         [("TRACK 1", "Sinóptico / VIA 1"),
                          ("BRETELLE 6+225 TRACK 2", "Bret. 6+225 / VIA 2")])
        self.assertIn(("hoja omitida: copia de la via 1 del Sinóptico", "VIA 1"),
                      {(d["motivo"], d["hoja"]) for d in master.discarded})
        self.assertEqual(master.unknown, {})

    def test_una_hoja_sin_declarar_no_pasa_en_silencio(self):
        master, _ = self.read(FakeSheet("Sinóptico", ROWS), FakeSheet("VIA 1", []),
                              FakeSheet("Bret. 6+225", BRETELLE), FakeSheet("VIA 3", []))
        self.assertEqual([(u["tipo"], u["valor"]) for u in master.unknown.values()],
                         [("hoja sinoptico sin declarar", "VIA 3")])


class CosechaDelCatalogo(unittest.TestCase):
    """El generador de LOVs lee la misma hoja con las mismas traducciones."""

    @classmethod
    def setUpClass(cls):
        cls.cat = blm.Catalogue(ALIASES)
        declared = {"format": "synoptic", "tracks": [
            {"sheet": "Sinóptico", "block": "VIA 1"}, {"sheet": "Sinóptico", "block": "VIA 2"},
            {"sheet": "Bret. 6+225", "block": "VIA 1"},
            {"sheet": "VIA 1", "skip": "copia de la via 1 del Sinóptico"}]}
        blm.read_synoptic_catalog([FakeSheet("Sinóptico", ROWS), FakeSheet("VIA 1", [])], "RUBI",
                                  declared, ALIASES, cls.cat)
        cls.rows = {(r["entity"], r["code"]): r for r in cls.cat.rows.values()}

    def test_los_codigos_llegan_traducidos_y_con_descripcion(self):
        self.assertIn(("Foundation", "M1"), self.rows)
        self.assertIn(("Sectioning", "OVERLAP"), self.rows)
        self.assertIn(("Sectioning", "TRANSITION"), self.rows)
        self.assertIn(("PoleType", "CANOPY"), self.rows)
        self.assertIn(("DisconnectorFunction", "SECT-I"), self.rows)
        self.assertEqual(self.rows[("AssemblyConfiguration", "C.F.21")]["desc_en"],
                         "Flexible catenary assembly configuration 21")
        self.assertEqual(self.rows[("Anchorage", "AnCONN")]["desc_en"],
                         "Connection anchorage (Santo Ovidio)")

    def test_todo_llega_como_origen_track(self):
        self.assertTrue(all(r["sources"] == {"TRACK"} for r in self.cat.rows.values()))
        self.assertEqual(self.rows[("SupportType", "OCR SUPPORT")]["track_uses"], 5)   # 2 + 3 Rígida

    def test_una_hoja_declarada_solo_para_omitirla_no_se_lee(self):
        cat = blm.Catalogue(ALIASES)
        declared = {"format": "synoptic", "tracks": [{"sheet": "VIA 1", "skip": "copia"}]}
        leidas = blm.read_synoptic_catalog([FakeSheet("VIA 1", ROWS)], "RUBI", declared, ALIASES, cat)
        self.assertEqual((leidas, cat.rows, cat.unknown), (0, {}, {}))

    def test_ninguna_grafia_del_origen_entra_al_catalogo(self):
        grafias = {synoptic.squash(k).upper()
                   for table in ALIASES["synoptic"]["translations"].values() for k, v in table.items()
                   if synoptic.squash(k).upper() != synoptic.squash(v or "").upper()}
        self.assertEqual({code.upper() for _, code in self.rows} & grafias, set())

    def test_los_codigos_del_sinoptico_estan_aceptados(self):
        for entity, code in self.rows:
            if entity == "AssemblyConfiguration" or code in {"OCR SUPPORT", "S/A", "A/S", "P30", "SECT-I"}:
                continue
            self.assertTrue(blm.is_track_accepted(entity, code, ALIASES), (entity, code))


if __name__ == "__main__":
    unittest.main()
