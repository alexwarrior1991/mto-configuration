"""Reglas de lectura del formato sinoptico (RUBI): una hoja con las dos vias en espejo.

Las hojas se construyen en memoria con la misma hoja de mentira que usan los tests del
maestro de perfiles. Las tablas de traduccion son las reales de aliases.yml: lo que se
prueba es justamente que una grafia portuguesa del origen NO llega al maestro.

    python3 -m unittest discover -s data/tools/tests
"""

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


# La cabecera real, reducida: la via 2 a la izquierda (leyendo hacia fuera), la banda
# central y la via 1 a la derecha. Las columnas None pegadas por fuera a 'Desalinhamentos',
# 'Alt. FC' y 'Consolas' son las gemelas de la segunda mensula.
HEADER = [
    1, "Comentarios", "Configuraciones", "Equipamentos associados ", "Equipamentos associados ",
    "Seccionamento", None, "Desalinhamentos (cm)", None, "Alt. FC (m)", None, "Consolas",
    "Maciços", "Tipo de Suporte", "Tipologia", "Implantação (m)", "Tipología",
    "P.K. \n(vía 2)", "Vão (m)", "SUPORTE",
    "VIA 2", None, None, "VIA 1",
    "SUPORTE", "Vão (m)", "P.K.\n(vía 1)", "Tipología", "Implantação (m)", "Tipologia",
    "Tipo de Suporte", "Maciços", "Consolas", None, "Alt. FC (m)", None,
    "Desalinhamentos (cm)", None, "Seccionamento", "Equipamentos associados ",
    "Equipamentos associados ", "Configuraciones", "Comentarios",
]
COL = {}
for _index, _name in enumerate(HEADER):
    if _name is not None:
        COL.setdefault(("R" if _index > HEADER.index("VIA 1") else "L", " ".join(str(_name).split())), _index)
COL[("L", "stagger2")] = HEADER.index("Desalinhamentos (cm)") - 1
COL[("L", "alt2")] = HEADER.index("Alt. FC (m)") - 1
COL[("L", "consola2")] = HEADER.index("Consolas") - 1
COL[("L", "equip2")] = 3
COL[("L", "equip")] = 4
COL[("R", "stagger2")] = COL[("R", "Desalinhamentos (cm)")] + 1
COL[("R", "alt2")] = COL[("R", "Alt. FC (m)")] + 1
COL[("R", "consola2")] = COL[("R", "Consolas")] + 1
COL[("R", "equip")] = 39
COL[("R", "equip2")] = 40
CENTRE = HEADER.index("VIA 2") + 1


def row(**cells):
    """Una fila con la columna A a 1, como el origen, y lo que se pida por nombre."""
    values = [None] * len(HEADER)
    values[0] = 1
    for key, value in cells.items():
        side, name = key.split("_", 1)
        values[COL[(side.upper(), name)]] = value
    return values


ROWS = [
    ["Linha Rubi"] + [None] * (len(HEADER) - 1),
    HEADER,
    # Fila 3: hito de trazado, sin KP.
    row(L_SUPORTE="Existente", **{"L_P.K. (vía 2)": "??"}, R_SUPORTE="Existente", **{"R_P.K. (vía 1)": "??"}),
    # Fila 4: KP negativo.
    row(L_SUPORTE="1-00.02", **{"L_P.K. (vía 2)": -2.84}, L_Tipologia="Rígida", **{"L_Alt. FC (m)": 3.8},
        R_SUPORTE="1-00.01", **{"R_P.K. (vía 1)": -2.84}, R_Tipologia="Rígida", **{"R_Alt. FC (m)": 3.8}),
    row(**{"L_Vão (m)": 6.2, "R_Vão (m)": 6.2}),
    # Fila 6: catenaria rigida con solape y segunda mensula en las gemelas.
    row(L_SUPORTE="3504CCA20", **{"L_P.K. (vía 2)": 1059.2}, L_Tipología="SE", L_Tipologia="Rígida",
        **{"L_Tipo de Suporte": "C860-A", "L_Desalinhamentos (cm)": -12, "L_Alt. FC (m)": 3.8},
        L_stagger2="+20", L_Seccionamento="Cantón 290 m",
        R_SUPORTE="3504CCA19", **{"R_P.K. (vía 1)": 1061}, R_Tipología="SE", R_Tipologia="Rígida",
        **{"R_Tipo de Suporte": "C860-A", "R_Desalinhamentos (cm)": -12, "R_Alt. FC (m)": 3.8},
        R_stagger2="+20", R_Seccionamento="Cantón 290 m"),
    row(**{"L_Vão (m)": 10, "R_Vão (m)": 10}, L_Seccionamento="Seccionamento", R_Seccionamento="Seccionamento"),
    # Fila 8: catenaria de mensula con KP en kilometracion y todo en portugues.
    row(L_SUPORTE="3505CCA02", **{"L_P.K. (vía 2)": "1+118,8"}, L_Tipología="Elev.", L_Tipologia="Consola",
        **{"L_Tipo de Suporte": "Pendulo 140", "L_Maciços": "Anc. Sot.", "L_Implantação (m)": "1,5",
           "L_Desalinhamentos (cm)": "+10", "L_Alt. FC (m)": 3.83},
        L_Consolas="B. Fun.", L_Configuraciones=0,
        R_SUPORTE="3505CCA01A", **{"R_P.K. (vía 1)": 1120.2}, R_Tipología="Elev.", R_Tipologia="Consola",
        **{"R_Tipo de Suporte": "Pendulo 140", "R_Maciços": "Anc. Sot.", "R_Implantação (m)": "1,5",
           "R_Desalinhamentos (cm)": "+10", "R_Alt. FC (m)": 3.83},
        R_Consolas="B. Fun.", R_Configuraciones=0),
    row(**{"L_Vão (m)": 10.6, "R_Vão (m)": 10.6}, L_equip="Disp. Transic.", R_equip="Disp. Transic.",
        L_Seccionamento="Transição Catenaria", R_Seccionamento="Transição Catenaria"),
    # Fila 10: catenaria flexible con dos mensulas, funcion doble y equipos repartidos.
    row(L_SUPORTE="3505CCA08", **{"L_P.K. (vía 2)": 1145.16}, L_Tipología="S/E A/S", L_Tipologia="Funicular",
        **{"L_Tipo de Suporte": "-", "L_Maciços": "Fixação Muro", "L_Desalinhamentos (cm)": "+20",
           "L_Alt. FC (m)": 3.8},
        L_Consolas="Delta", L_consola2="B.Fun.", L_stagger2="+10", L_alt2=3.84,
        L_Configuraciones="C.C,2", L_equip="Ancoraem 2HC", L_equip2="Caix. Imped.",
        R_SUPORTE="3505CCA07", **{"R_P.K. (vía 1)": 1146.96}, R_Tipología="S/E A/S", R_Tipologia="Funicular",
        **{"R_Tipo de Suporte": "-", "R_Maciços": "Fixação Muro", "R_Desalinhamentos (cm)": "+20",
           "R_Alt. FC (m)": 3.8},
        R_Consolas="Delta", R_consola2="B.Fun.", R_stagger2="+10", R_alt2=3.84,
        R_Configuraciones="C.C,2", R_equip="Ancoraem 2HC", R_equip2="Caix. Imped."),
    # Fila 11: la errata 'Anc' y un anclaje de conexion.
    row(R_SUPORTE="3713CCA09", **{"R_P.K. (vía 1)": 6392}, R_Tipología="Anc", R_Tipologia="-",
        **{"R_Tipo de Suporte": "HEA 280 G.", "R_Maciços": "Poste parede"},
        R_equip="Ancoraem coneção Sto Ov.", R_Configuraciones="C.F.21"),
    # Fila 12: el aislador escrito en la funcion del apoyo, el maciço M1 y la marquesina.
    row(R_SUPORTE="3711CCA113", **{"R_P.K. (vía 1)": 6179.2}, R_Tipología="Aislad.", R_Tipologia="Rígida",
        **{"R_Tipo de Suporte": "Marquise", "R_Maciços": "M1", "R_Desalinhamentos (cm)": "+4",
           "R_Alt. FC (m)": 3.8}),
    # Fila 13: aguja PA30 y mensula doble; en la via 2 un '-' donde iria el codigo.
    row(L_SUPORTE="-", **{"L_P.K. (vía 2)": 6376.9},
        R_SUPORTE="3713CCA15", **{"R_P.K. (vía 1)": 6441.5}, R_Tipología="PA30", R_Tipologia="Cons. Dob.",
        **{"R_Implantação (m)": "6,73", "R_Maciços": "Anc. Pavi."}),
    # Fila 14: en blanco en los dos bloques: fin de los datos.
    row(),
    # Fila 15: notas al pie, que escriben un codigo y un KP donde iria un apoyo.
    row(L_SUPORTE="3703CCA36", **{"L_P.K. (vía 2)": 2527.9}, R_Tipología="No es A/S con elevacion"),
]

CATALOG = {
    "Sectioning": {"S/A": "S/A", "A/S": "A/S", "MP": "MP", "A": "A", "P30": "P30", "P50": "P50",
                   "P80": "P80", "OVERLAP": "OVERLAP", "TRANSITION": "TRANSITION"},
    "Anchorage": {"AN1CW": "An1CW", "AN2CW": "An2CW", "ANCONN": "AnCONN"},
    "DisconnectorFunction": {"SECT-I": "SECT-I", "SECTCONNBOX": "SectConnBox", "IMPBOX": "ImpBox",
                             "TRANSDEV": "TransDev", "SECT-I-1CW": "SECT-I-1CW", "SECT-I-2CW": "SECT-I-2CW"},
    "SupportType": {"OCR SUPPORT": "OCR SUPPORT", "FLEXIBLE": "FLEXIBLE",
                    "SINGLE CANTILEVER": "SINGLE CANTILEVER", "DOUBLE CANTILEVER": "DOUBLE CANTILEVER"},
    "PoleType": {"C860-A": "C860-A", "PENDULO 140": "Pendulo 140", "HEA 280 G.": "HEA 280 G.",
                 "CANOPY": "CANOPY"},
    "Foundation": {"M1": "M1", "UNDERGROUND ANCHOR": "UNDERGROUND ANCHOR", "WALL FIXING": "WALL FIXING",
                   "WALL POLE": "WALL POLE", "PAVEMENT ANCHOR": "PAVEMENT ANCHOR"},
    "AssemblyConfiguration": {"C.C.2": "C.C.2", "C.F.21": "C.F.21"},
    "CantileverType": {"OCR": "OCR", "UNKNOWN": "UNKNOWN", "DELTA": "DELTA",
                       "FLEXIBLE ARM": "FLEXIBLE ARM"},
}

CFG = dict(ALIASES, defaults={"profile_status": "DEFINITIVE"}, lov_catalog=CATALOG)


def read(block, name):
    master = bpm.Master()
    sheet = FakeSheet("Sinóptico", ROWS)
    profiles, cantilevers = bpm.read_synoptic_track(
        sheet, "RUBI", {"block": block, "name": name}, CFG, master)
    return master, profiles, cantilevers


class PlanoDeLaHoja(unittest.TestCase):
    """Los dos bloques se parten por los marcadores y las columnas se resuelven por nombre."""

    def setUp(self):
        self.master = bpm.Master()
        self.layout = synoptic.synoptic_layout(FakeSheet("Sinóptico", ROWS), "RUBI", CFG, self.master)

    def test_la_cabecera_se_localiza_por_contenido_y_hay_dos_bloques(self):
        self.assertEqual(self.layout.header_index, 1)
        self.assertEqual(set(self.layout.blocks), {"VIA 1", "VIA 2"})
        self.assertEqual(self.master.unknown, {})

    def test_cada_bloque_resuelve_sus_columnas_por_nombre(self):
        left, right = self.layout.blocks["VIA 2"], self.layout.blocks["VIA 1"]
        self.assertEqual(left.single["PROFILE_ID"], COL[("L", "SUPORTE")])
        self.assertEqual(right.single["PROFILE_ID"], COL[("R", "SUPORTE")])
        self.assertEqual(left.single["KP"], COL[("L", "P.K. (vía 2)")])
        self.assertEqual(right.single["KP"], COL[("R", "P.K. (vía 1)")])
        # Con tilde y sin tilde son dos columnas distintas.
        self.assertEqual(right.single["SUPPORT_TYPE"], COL[("R", "Tipologia")])
        self.assertIn(COL[("R", "Tipología")], right.multi["SECTIONING"])
        self.assertIn(COL[("R", "Seccionamento")], right.multi["SECTIONING"])

    def test_las_gemelas_son_la_columna_sin_cabecera_pegada_por_fuera(self):
        left, right = self.layout.blocks["VIA 2"], self.layout.blocks["VIA 1"]
        self.assertEqual(left.twins["STAGGER"], COL[("L", "stagger2")])
        self.assertEqual(left.twins["CW_HEIGHT"], COL[("L", "alt2")])
        self.assertEqual(left.twins["CANTILEVER_TYPE"], COL[("L", "consola2")])
        self.assertEqual(right.twins["STAGGER"], COL[("R", "stagger2")])
        self.assertEqual(right.twins["CANTILEVER_TYPE"], COL[("R", "consola2")])
        # La cabecera repetida: la principal es la de dentro y la otra su gemela.
        self.assertEqual(sorted(left.multi["EQUIPMENT"]), [COL[("L", "equip2")], COL[("L", "equip")]])
        self.assertEqual(sorted(right.multi["EQUIPMENT"]), [COL[("R", "equip")], COL[("R", "equip2")]])

    def test_la_banda_central_y_el_fin_de_los_datos(self):
        self.assertEqual(self.layout.centre, [CENTRE, CENTRE + 1])
        self.assertEqual(self.layout.data_end, len(ROWS) - 2)   # la fila en blanco

    def test_una_cabecera_desconocida_no_pasa_en_silencio(self):
        rows = [list(r) for r in ROWS]
        rows[1][COL[("R", "Comentarios")]] = "Columna nueva"
        master = bpm.Master()
        synoptic.synoptic_layout(FakeSheet("Sinóptico", rows), "RUBI", CFG, master)
        self.assertEqual([u["valor"] for u in master.unknown.values()], ["columna nueva"])

    def test_un_ep_sin_format_sigue_por_el_camino_de_las_hojas_hr_track(self):
        self.assertFalse(synoptic.is_synoptic({"file": "EP6.xlsm"}))
        self.assertFalse(synoptic.is_synoptic(None))
        self.assertTrue(synoptic.is_synoptic({"format": "synoptic"}))


class KilometracionDelSinoptico(unittest.TestCase):

    def test_numero_coma_y_kilometracion_con_mas(self):
        self.assertEqual(synoptic.parse_kp(1061), Decimal("1061"))
        self.assertEqual(synoptic.parse_kp(1059.2), Decimal("1059.2"))
        self.assertEqual(synoptic.parse_kp("1+118,8"), Decimal("1118.8"))
        self.assertEqual(synoptic.parse_kp("6 392,5"), Decimal("6392.5"))

    def test_los_huecos_no_son_un_kp(self):
        for value in (None, "", "??", "-", "abc", True):
            self.assertIsNone(synoptic.parse_kp(value), repr(value))


class LecturaDeUnaVia(unittest.TestCase):
    """La via 1: el bloque de la derecha."""

    @classmethod
    def setUpClass(cls):
        cls.master, cls.profiles, cls.cantilevers = read("VIA 1", "TRACK 1")
        cls.by_id = {p["PROFILE_ID"]: p for p in cls.master.profiles}

    def test_solo_los_apoyos_son_perfiles_y_en_su_orden(self):
        self.assertEqual([p["PROFILE_ID"] for p in self.master.profiles],
                         ["1-00.01", "3504CCA19", "3505CCA01A", "3505CCA07", "3713CCA09",
                          "3711CCA113", "3713CCA15"])
        self.assertEqual([p["ORDEN"] for p in self.master.profiles], [1, 2, 3, 4, 5, 6, 7])
        self.assertTrue(all(p["VIA"] == "TRACK 1" for p in self.master.profiles))
        self.assertTrue(all(p["HOJA_ORIGEN"] == "Sinóptico / VIA 1" for p in self.master.profiles))

    def test_el_hito_y_la_nota_al_pie_se_descartan_con_motivo(self):
        motivos = {(d["motivo"], d["fila"]) for d in self.master.discarded}
        self.assertIn(("hito de trazado, no es un apoyo", 3), motivos)
        self.assertIn(("fila fuera del bloque de datos", 15), motivos)

    def test_el_kp_negativo_deja_el_perfil_sin_cargar(self):
        perfil = self.by_id["1-00.01"]
        self.assertIsNone(perfil["KP"])
        self.assertEqual((perfil["ENABLED"], perfil["REVISAR"]), ("NO", "SI"))
        self.assertIn(("KP: negativo", 4), {(d["motivo"], d["fila"]) for d in self.master.discarded})

    def test_el_vano_y_el_seccionamento_vienen_de_la_fila_intermedia(self):
        perfil = self.by_id["3504CCA19"]
        self.assertEqual(perfil["SPAN"], Decimal("10.000"))
        self.assertEqual(perfil["SECTIONING"], "S/A|OVERLAP")     # SE + 'Seccionamento'
        self.assertEqual(perfil["SUPPORT_TYPE"], "OCR SUPPORT")   # Rígida
        self.assertEqual(perfil["POLE_TYPE"], "C860-A")

    def test_la_longitud_del_canton_no_es_un_codigo_y_se_conserva(self):
        anotaciones = [(u["COLUMNA"], u["VALOR"]) for u in self.master.unmapped
                       if u["PROFILE_ID"] == "3504CCA19"]
        self.assertIn(("Seccionamento", "Cantón 290 m"), anotaciones)

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
        self.assertEqual(perfil["FOUNDATION"], "WALL FIXING")           # Fixação Muro
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

    def test_las_mensulas_con_su_gemela_y_el_fallback_de_la_rigida(self):
        por_perfil = {}
        for c in self.master.cantilevers:
            por_perfil.setdefault(c["PROFILE_ID"], []).append(
                (c["SLOT"], c["CANTILEVER_TYPE"], c["STAGGER"], c["CW_HEIGHT"]))
        # Rigida sin consola: la mensula es OCR, deducida del soporte, y la gemela es la segunda.
        self.assertEqual(por_perfil["3504CCA19"],
                         [(1, "OCR", Decimal("-12"), Decimal("3.800")), (2, "OCR", Decimal("20"), None)])
        # Flexible con dos consolas escritas.
        self.assertEqual(por_perfil["3505CCA07"],
                         [(1, "DELTA", Decimal("20"), Decimal("3.800")),
                          (2, "FLEXIBLE ARM", Decimal("10"), Decimal("3.840"))])
        self.assertEqual(por_perfil["3505CCA01A"], [(1, "FLEXIBLE ARM", Decimal("10"), Decimal("3.830"))])
        # El apoyo con KP negativo no carga sus mensulas: solo traia la altura, sin evidencia.
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
    """La via 2: el bloque de la izquierda, leyendo hacia fuera."""

    @classmethod
    def setUpClass(cls):
        cls.master, _, _ = read("VIA 2", "TRACK 2")
        cls.by_id = {p["PROFILE_ID"]: p for p in cls.master.profiles}

    def test_los_mismos_apoyos_con_su_propio_kp(self):
        self.assertEqual([p["PROFILE_ID"] for p in self.master.profiles],
                         ["1-00.02", "3504CCA20", "3505CCA02", "3505CCA08"])
        self.assertEqual(self.by_id["3505CCA02"]["KP"], Decimal("1118.800"))   # '1+118,8'

    def test_las_gemelas_de_la_izquierda_estan_hacia_fuera(self):
        mensulas = [(c["SLOT"], c["CANTILEVER_TYPE"], c["STAGGER"]) for c in self.master.cantilevers
                    if c["PROFILE_ID"] == "3505CCA08"]
        self.assertEqual(mensulas, [(1, "DELTA", Decimal("20")), (2, "FLEXIBLE ARM", Decimal("10"))])

    def test_un_guion_donde_iria_el_codigo_no_es_un_apoyo(self):
        self.assertNotIn("-", self.by_id)
        self.assertEqual(self.by_id["3505CCA08"]["SPAN"], None)

    def test_la_nota_al_pie_no_es_un_apoyo_aunque_traiga_codigo_y_kp(self):
        self.assertNotIn("3703CCA36", self.by_id)
        self.assertIn(("fila fuera del bloque de datos", "3703CCA36"),
                      {(d["motivo"], d["detalle"]) for d in self.master.discarded})


class CosechaDelCatalogo(unittest.TestCase):
    """El generador de LOVs lee la misma hoja con las mismas traducciones."""

    @classmethod
    def setUpClass(cls):
        cls.cat = blm.Catalogue(ALIASES)
        declared = {"format": "synoptic", "tracks": [{"sheet": "Sinóptico", "block": "VIA 1"},
                                                     {"sheet": "Sinóptico", "block": "VIA 2"}]}
        blm.read_synoptic_catalog(FakeSheet("Sinóptico", ROWS), "RUBI", declared, ALIASES, cls.cat)
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
