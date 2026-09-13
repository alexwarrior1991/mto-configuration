"""Reglas de lectura del generador del maestro de perfiles.

Las hojas se construyen en memoria con una hoja de mentira: no hace falta abrir un
workbook para probar que el vano se toma de la fila intermedia o que una hoja sin
columna 'Sectionning' no desplaza el resto.

La ultima clase contrasta el data/profile-master.xlsx ya generado y se salta sola si
no esta.

    python3 -m unittest discover -s data/tools/tests
"""

import importlib.util
import os
import unittest
from decimal import Decimal

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.dirname(BASE)
MASTER = os.path.join(DATA, "profile-master.xlsx")
LOV_MASTER = os.path.join(DATA, "lov-master.xlsx")


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(BASE, name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bpm = _load("build_profile_master")
common = _load("workbook_common")

ARM_TYPES = ["BC", "BCE", "BS", "BTC", "PH", "PH-C", "PH-Q", "PHC", "PHQ"]

# Cabecera minima con los grupos de tres columnas donde los pone el origen.
HEADER = ["Survey", "Profile", "KP", "Span", "Sectionning", "Pole Type", "Cantilevers",
          None, None, "Stagger", None, None, "Arm Type", None, None, "Soil Found",
          "Supports", "CW height", None, None]
# Misma hoja SIN la columna 'Sectionning': es el caso real de EP6 / HR Track 1 HER,
# donde todo lo posterior queda desplazado una posicion.
HEADER_SHIFTED = [h for h in HEADER if h != "Sectionning"]

CFG = {
    "profile_columns": {
        "profile": {"profile": "PROFILE_ID", "kp": "KP", "span": "SPAN",
                    "sectionning": "SECTIONING", "pole type": "POLE_TYPE"},
        "cantilever": {"cantilevers": "CANTILEVER_TYPE", "stagger": "STAGGER",
                       "arm type": "STEADY_ARM", "cw height": "CW_HEIGHT"},
        "unmapped": ["survey", "soil found", "supports"],
    },
    "steady_arm_types": ARM_TYPES,
    "defaults": {"profile_status": "DEFINITIVE"},
}


class FakeSheet:
    """Lo minimo de una hoja de openpyxl que usa el generador."""

    def __init__(self, title, rows):
        self.title = title
        self._rows = rows
        self.max_row = len(rows)

    def iter_rows(self, min_row=1, max_row=None, max_col=None, values_only=True):
        end = min(max_row or len(self._rows), len(self._rows))
        for row in self._rows[min_row - 1:end]:
            yield tuple(row[:max_col]) if max_col else tuple(row)


def pad(values, width=len(HEADER)):
    return list(values) + [None] * (width - len(values))


class ParticionDelBrazo(unittest.TestCase):
    """La columna 'Arm Type' trae tipo y longitud juntos y hay que separarlos.

    La regla es "sufijo numerico = longitud", y necesita la lista de tipos porque
    'PH-C' y 'PH-Q' llevan guion sin que eso sea una longitud.
    """

    def split(self, raw):
        return bpm.split_steady_arm(raw, ARM_TYPES)

    def test_tipo_y_longitud(self):
        self.assertEqual(self.split("PH-1150"), ("PH", 1150, None))
        self.assertEqual(self.split("BTC-1651"), ("BTC", 1651, None))

    def test_un_tipo_con_guion_no_se_parte(self):
        # Sin la lista de tipos, 'PH-C' se leeria como 'PH' con una longitud rara.
        self.assertEqual(self.split("PH-C"), ("PH-C", None, None))
        self.assertEqual(self.split("PH-Q"), ("PH-Q", None, None))

    def test_solo_el_tipo_es_normal(self):
        # 5.691 de las 14.592 mensulas del origen estan asi: la longitud no se
        # conoce, y por eso steady_arm.length es opcional.
        self.assertEqual(self.split("PH"), ("PH", None, None))

    def test_se_toleran_las_grafias_sucias(self):
        self.assertEqual(self.split("PH- 1450"), ("PH", 1450, None))   # espacio suelto
        self.assertEqual(self.split("PH950"), ("PH", 950, None))       # sin guion
        self.assertEqual(self.split("BTC_1651"), ("BTC", 1651, None))  # guion bajo

    def test_una_letra_final_se_ignora_pero_se_dice(self):
        arm_type, length, reason = self.split("PHC-1500E")
        self.assertEqual((arm_type, length), ("PHC", 1500))
        self.assertIn("sufijo ignorado", reason)

    def test_una_longitud_imposible_no_se_carga(self):
        arm_type, length, reason = self.split("BC-2001")
        self.assertEqual(arm_type, "BC")
        self.assertIsNone(length, "la columna admite 1..2000")
        self.assertIn("2001", reason)

    def test_un_tipo_desconocido_se_reporta(self):
        arm_type, length, reason = self.split("BHC-1150")
        self.assertIsNone(arm_type)
        self.assertIn("desconocido", reason)

    def test_una_celda_vacia_o_de_ruido_no_es_un_error(self):
        for raw in (None, "", "0", "-", "#NAME?", "False"):
            self.assertEqual(self.split(raw), (None, None, None), raw)


class NumerosQueTienenQueCaberEnSuColumna(unittest.TestCase):
    """Aceptar aqui mas de lo que admite la columna solo cambia un 400 por un 500."""

    def setUp(self):
        self.master = bpm.Master()

    def fit(self, field, value):
        return bpm.fit_numeric(field, value, ep="EP1", sheet="H", row=7, master=self.master)

    def test_redondea_a_la_escala_de_la_columna(self):
        # El origen trae ruido de coma flotante en valores que son milimetros enteros.
        value, bad = self.fit("POLE_GAUGE_LOCATION", 1475.0000000000002)
        self.assertEqual(value, Decimal("1475"))
        self.assertFalse(bad)

        value, _ = self.fit("ARM_ANGLE", 12.386483251131668)
        self.assertEqual(value, Decimal("12.386"))

    def test_lo_que_no_cabe_se_pierde_solo_ese_valor(self):
        # Un perfil con una altura de catenaria imposible sigue siendo un perfil.
        value, bad = self.fit("CATENARY_HEIGHT", 18.0)
        self.assertIsNone(value)
        self.assertTrue(bad)
        self.assertIn("no cabe", self.master.discarded[0]["motivo"])

    def test_un_negativo_se_rechaza_donde_no_tiene_sentido(self):
        value, bad = self.fit("SPAN", -15)
        self.assertIsNone(value)
        self.assertTrue(bad)

    def test_la_distancia_carril_poste_si_admite_signo(self):
        # El signo indica a que lado de la via queda el poste.
        value, bad = self.fit("RAIL_POLE_DISTANCE", -4960)
        self.assertEqual(value, Decimal("-4960"))
        self.assertFalse(bad)

    def test_el_angulo_del_brazo_esta_acotado(self):
        self.assertEqual(self.fit("ARM_ANGLE", 89.999)[0], Decimal("89.999"))
        self.assertIsNone(self.fit("ARM_ANGLE", 91)[0])

    def test_una_celda_vacia_no_genera_descarte(self):
        for raw in (None, "", "0", "-"):
            value, bad = self.fit("SPAN", raw)
            self.assertIsNone(value)
            self.assertFalse(bad, raw)
        self.assertEqual(self.master.discarded, [])

    def test_un_texto_donde_va_un_numero_se_reporta(self):
        value, bad = self.fit("ARM_ANGLE", "#DIV/0!")
        self.assertIsNone(value)
        self.assertTrue(bad)
        self.assertIn("no es un numero", self.master.discarded[0]["motivo"])


class ResolucionDeColumnasPorNombre(unittest.TestCase):
    """Por nombre y nunca por indice: hay una hoja con una columna de menos."""

    def resolve(self, header):
        master = bpm.Master()
        single, multi, unmapped = bpm.resolve_columns(header, CFG, "EP1", "H", master)
        return single, multi, unmapped, master

    def test_las_columnas_simples_caen_donde_toca(self):
        single, _, _, _ = self.resolve(HEADER)
        self.assertEqual(single["PROFILE_ID"], 1)
        self.assertEqual(single["POLE_TYPE"], 5)

    def test_una_hoja_sin_sectionning_no_desplaza_el_resto(self):
        # Es EP6 / HR Track 1 HER. Leyendo por indice, su 'Pole Type' se leeria de la
        # columna de 'Sectionning' y toda la hoja saldria corrida.
        single, _, _, _ = self.resolve(HEADER_SHIFTED)
        self.assertNotIn("SECTIONING", single)
        self.assertEqual(single["POLE_TYPE"], 4, "una posicion menos, resuelto por nombre")

    def test_una_cabecera_de_grupo_ocupa_tres_columnas(self):
        _, multi, _, _ = self.resolve(HEADER)
        self.assertEqual(multi["CANTILEVER_TYPE"], [6, 7, 8])
        self.assertEqual(multi["STAGGER"], [9, 10, 11])

    def test_las_columnas_sin_campo_en_el_dominio_se_recogen_aparte(self):
        _, _, unmapped, _ = self.resolve(HEADER)
        self.assertEqual(set(unmapped), {"Survey", "Soil Found", "Supports"})

    def test_una_cabecera_desconocida_no_pasa_en_silencio(self):
        _, _, _, master = self.resolve(HEADER + ["Columna Que No Existe"])
        self.assertEqual(len(master.unknown), 1)
        self.assertIn("cabecera Track", next(iter(master.unknown))[0])


class LecturaDeUnaHojaDeTrazado(unittest.TestCase):
    """Las filas alternan: perfil, fila intermedia con el vano, perfil, ..."""

    def read(self, rows, decl=None, cfg=None):
        master = bpm.Master()
        sheet = FakeSheet("HR Track 1", [pad(r) for r in rows])
        profiles, cantilevers = bpm.read_track(sheet, "EP1", decl or {"name": "VIA 1"},
                                               cfg or CFG, master)
        return master, profiles, cantilevers

    @staticmethod
    def sheet_rows():
        return [
            ["TRACK 1"],                                            # 1: titulo
            HEADER,                                                 # 2: cabecera
            [None, None, None, None, None, None, "M1", "M2", "M3"],  # 3: subcabecera
            ["Section C", "30-1.15", 30675, None, "A/S", "S1T",
             "EMT-2T", None, None, 20, None, None, "PH-950"],       # 4: perfil
            [None, None, None, 41],                                 # 5: fila intermedia
            [None, "30-1.16", 30716, None, "S/A", "S1T",
             "EMT-1", "EMT-T", None, 15, -30, None, "PH", "PHQ"],   # 6: perfil, 2 mensulas
            ["0", "0", "0", "0", "0", "0"],                         # 7: fila de ceros
        ]

    def test_el_vano_se_toma_de_la_fila_intermedia(self):
        master, profiles, _ = self.read(self.sheet_rows())
        self.assertEqual(profiles, 2)
        self.assertEqual(master.profiles[0]["SPAN"], Decimal("41.000"))

    def test_el_ultimo_perfil_se_queda_sin_vano(self):
        # No hay fila intermedia detras, y eso no es un error: el vano es hasta el
        # perfil siguiente, y no hay siguiente.
        master, _, _ = self.read(self.sheet_rows())
        self.assertIsNone(master.profiles[1]["SPAN"])

    def test_las_filas_de_ceros_no_son_perfiles(self):
        # El origen usa '0' como marcador de hueco en TODAS las columnas, incluida la
        # del identificador. Sin esto entrarian ~320 perfiles fantasma.
        master, profiles, _ = self.read(self.sheet_rows())
        self.assertEqual(profiles, 2)
        self.assertNotIn("0", [p["PROFILE_ID"] for p in master.profiles])

    def test_un_perfil_puede_traer_varias_mensulas(self):
        master, _, cantilevers = self.read(self.sheet_rows())
        self.assertEqual(cantilevers, 3)
        slots = [(c["PROFILE_ID"], c["SLOT"], c["CANTILEVER_TYPE"]) for c in master.cantilevers]
        self.assertEqual(slots, [("30-1.15", 1, "EMT-2T"),
                                 ("30-1.16", 1, "EMT-1"),
                                 ("30-1.16", 2, "EMT-T")])

    def test_un_slot_sin_tipo_no_es_una_mensula_aunque_traiga_medidas(self):
        """Sin tipo no hay mensula, y entonces sus parametros son ruido de la hoja.

        Si el perfil lleva mensula en M1 y M2, un valor suelto en M3 esta mal puesto: no
        es una mensula a la que le falte el tipo. Se tira el slot ENTERO —medidas y
        brazo— y se anota en DESCARTADOS, que es lo que impide que desaparezca en
        silencio. En el maestro real son 348 slots.
        """
        rows = self.sheet_rows()
        # Al perfil de dos mensulas se le mete una desviacion y un brazo en M3, sin tipo.
        rows[5] = [None, "30-1.16", 30716, None, "S/A", "S1T",
                   "EMT-1", "EMT-T", None, 15, -30, 40, "PH", "PHQ", "BC-1200"]
        master, _, cantilevers = self.read(rows)

        self.assertEqual(cantilevers, 3, "el slot 3 no llega a ser mensula")
        self.assertEqual([c["SLOT"] for c in master.cantilevers], [1, 1, 2])

        tirados = [d for d in master.discarded if "slot sin tipo" in d["motivo"]]
        self.assertEqual(len(tirados), 1)
        self.assertIn("slot 3", tirados[0]["detalle"])
        self.assertIn("STAGGER=40", tirados[0]["detalle"])

    def test_un_poste_sin_mensulas_es_normal_y_no_se_descarta(self):
        """Un poste que solo hace de anclaje no lleva mensulas, y eso no es un error.

        No se anota nada: no hay nada que tirar. El perfil entra igual, sin mensulas.
        """
        rows = self.sheet_rows()
        rows[3] = ["Section C", "30-1.15", 30675, None, "A/S", "S1T"]  # sin nada de mensula
        master, profiles, cantilevers = self.read(rows)

        self.assertEqual(profiles, 2)
        self.assertEqual([c["PROFILE_ID"] for c in master.cantilevers], ["30-1.16", "30-1.16"])
        self.assertEqual([d for d in master.discarded if "slot sin tipo" in d["motivo"]], [])

    # --- Tipo de mensula supuesto ---------------------------------------------------
    #
    # El CFG de los tests de arriba no trae 'cantilever_type_fallback', asi que alli la
    # regla no actua y el slot sin tipo se sigue tirando: es a proposito, para que las
    # dos conductas queden probadas por separado.

    CFG_FALLBACK = {
        "default": "UNKNOWN",
        "by_support": {"OCR SUPPORT": "OCR"},
        "evidence": ["STAGGER", "CATENARY_HEIGHT", "CW_ELEVATION", "WIND_DEFLECTION",
                     "STEADY_ARM"],
    }

    def leer_con_fallback(self, rows):
        """El valor de 'Supports' viaja en la fila, en su columna, como en el origen."""
        cfg = dict(CFG,
                   cantilever_type_fallback=self.CFG_FALLBACK,
                   lov_catalog={"CantileverType": {"UNKNOWN": "UNKNOWN", "OCR": "OCR",
                                                   "EMT-1": "EMT-1", "EMT-T": "EMT-T",
                                                   "EMT-2T": "EMT-2T"}})
        return self.read(rows, cfg=cfg)

    def test_un_slot_con_medidas_propias_y_sin_tipo_recibe_UNKNOWN(self):
        """La mensula ESTA; lo que falta es su nombre, que no es lo mismo.

        Una desviacion de -30 cm describe una mensula puesta: no se rellena donde no hay
        ninguna. Sin un tipo al que colgarla, la mensula se perdia ENTERA —217 en el
        maestro real—, porque el tipo es una relacion obligatoria. Entra marcada
        REVISAR=SI: el tipo no viene del origen y eso tiene que verse.
        """
        rows = self.sheet_rows()
        rows[5] = pad([None, "30-1.16", 30716, None, "S/A", "S1T",
                       "EMT-1", "EMT-T", None, 15, -30, 40, "PH", "PHQ", "BC-1200"])
        master, _, cantilevers = self.leer_con_fallback(rows)

        self.assertEqual(cantilevers, 4)
        tercera = [c for c in master.cantilevers if c["SLOT"] == 3][0]
        self.assertEqual(tercera["CANTILEVER_TYPE"], "UNKNOWN")
        self.assertEqual(tercera["STAGGER"], Decimal("40"))
        self.assertEqual(tercera["REVISAR"], "SI")
        self.assertEqual([d for d in master.discarded if "slot sin tipo" in d["motivo"]], [])
        supuestos = [d for d in master.discarded if "tipo de mensula supuesto" in d["motivo"]]
        self.assertEqual(len(supuestos), 1)
        self.assertIn("UNKNOWN", supuestos[0]["detalle"])

    def test_en_catenaria_rigida_el_tipo_sale_de_la_columna_Supports(self):
        """'OCR SUPPORT' es catenaria rigida, y su mensula es 'OCR', que ya esta en el
        catalogo. No es un tipo desconocido: es uno que el origen deja de escribir en la
        columna del tipo y escribe en la de al lado.
        """
        rows = self.sheet_rows()
        rows[5] = pad([None, "30-1.16", 30716, None, "S/A", "S1T",
                       "EMT-1", "EMT-T", None, 15, -30, 40, "PH", "PHQ", "BC-1200"])
        rows[5][16] = "OCR SUPPORT"        # la columna Supports de esa misma fila
        master, _, _ = self.leer_con_fallback(rows)

        tercera = [c for c in master.cantilevers if c["SLOT"] == 3][0]
        self.assertEqual(tercera["CANTILEVER_TYPE"], "OCR")
        self.assertEqual(tercera["REVISAR"], "SI")

    def test_un_slot_que_solo_trae_cwHeight_no_recibe_tipo(self):
        """cwHeight es un valor de proyecto que se repite a lo largo del tramo y aparece
        igual donde no hay ninguna mensula. Por si solo no prueba nada, asi que el slot
        se sigue tirando entero aunque la regla del tipo supuesto este activa.
        """
        rows = self.sheet_rows()
        # Solo CW_HEIGHT en el slot 3 (columna 19): ni desviacion, ni brazo, ni tipo.
        rows[5] = pad([None, "30-1.16", 30716, None, "S/A", "S1T",
                       "EMT-1", "EMT-T", None, 15, -30, None, "PH", "PHQ", None])
        rows[5][19] = 5.5
        master, _, cantilevers = self.leer_con_fallback(rows)

        self.assertEqual(cantilevers, 3, "el slot 3 sigue sin ser mensula")
        self.assertEqual(len(
            [d for d in master.discarded if "slot sin tipo" in d["motivo"]]), 1)

    def test_una_errata_del_tipo_de_brazo_no_pierde_el_brazo(self):
        """'BHC-1150' es 'PHC-1150' mal tecleado: se corrige, no se da de alta.

        El alias tiene que aplicarse ANTES de separar el tipo de la longitud, porque
        'BHC-1150' no llega entero a ningun sitio donde una tabla de grafias pudiera
        verlo. Sin esto el brazo se perdia con un "tipo de brazo desconocido", y con el
        se perdia tambien la longitud, que si era buena.
        """
        self.assertEqual(bpm.split_steady_arm("BHC-1150", ARM_TYPES, {"BHC": "PHC"}),
                         ("PHC", 1150, None))
        # Y sin declararlo sigue siendo un tipo desconocido, que es lo que hace falta
        # para que a nadie se le cuele una errata nueva.
        tipo, longitud, motivo = bpm.split_steady_arm("BHC-1150", ARM_TYPES)
        self.assertIsNone(tipo)
        self.assertIn("desconocido", motivo)

    def test_el_brazo_se_parte_al_leer(self):
        master, _, _ = self.read(self.sheet_rows())
        first = master.cantilevers[0]
        self.assertEqual((first["STEADY_ARM_TYPE"], first["STEADY_ARM_LENGTH"]), ("PH", 950))

    def test_la_fila_de_origen_es_la_que_enseña_excel(self):
        # Sin esto, quien revisa el maestro no puede ir a la celda.
        master, _, _ = self.read(self.sheet_rows())
        self.assertEqual(master.profiles[0]["FILA_ORIGEN"], 4)

    def test_un_tramo_declarado_recorta_la_hoja(self):
        # Es lo que separa los dos tramos concatenados de EP9A / HR Track 1.
        master, profiles, _ = self.read(self.sheet_rows(),
                                        decl={"name": "VIA 1", "rows": [6, 6]})
        self.assertEqual(profiles, 1)
        self.assertEqual(master.profiles[0]["PROFILE_ID"], "30-1.16")

    def test_las_columnas_sin_destino_se_conservan(self):
        master, _, _ = self.read(self.sheet_rows())
        survey = [u for u in master.unmapped if u["COLUMNA"] == "Survey"]
        self.assertEqual([u["VALOR"] for u in survey], ["Section C"])

    def test_un_perfil_sin_kp_no_se_carga(self):
        rows = self.sheet_rows()
        rows[3] = ["", "30-1.15", None, None, "A/S"]
        master, _, _ = self.read(rows)
        self.assertEqual(master.profiles[0]["ENABLED"], "NO")
        self.assertEqual(master.profiles[0]["REVISAR"], "SI")


class EstacionesDeclaradas(unittest.TestCase):
    """Una via no puede colgar de una estacion que su paquete no declara.

    El importador la rechaza y esa via se queda sin cargar, asi que vale mas enterarse
    generando el maestro que descubrirlo con el trabajo a medias.
    """

    def check(self, stations, tracks):
        master = bpm.Master()
        bpm.check_declared_stations("EP6", {"stations": stations, "tracks": tracks}, master)
        return master

    def test_una_estacion_declarada_pasa(self):
        master = self.check(["HERZLIYA"], [{"sheet": "H", "station": "HERZLIYA"}])
        self.assertEqual(master.unknown, {})

    def test_ignora_mayusculas_y_espacios(self):
        master = self.check(["HERZLIYA"], [{"sheet": "H", "station": " herzliya "}])
        self.assertEqual(master.unknown, {})

    def test_una_estacion_inventada_no_pasa_en_silencio(self):
        master = self.check(["HERZLIYA"], [{"sheet": "H", "station": "NO EXISTE"}])
        self.assertEqual(len(master.unknown), 1)
        self.assertIn("estacion no declarada", next(iter(master.unknown))[0])

    def test_sin_estacion_es_una_respuesta_valida(self):
        # TRACK.STATION_ID es anulable a proposito: una via de tramo entre estaciones
        # cuelga del paquete de ejecucion.
        master = self.check([], [{"sheet": "H", "station": None}])
        self.assertEqual(master.unknown, {})

    def test_una_hoja_omitida_no_se_comprueba(self):
        master = self.check([], [{"sheet": "H", "station": "NO EXISTE", "skip": "vacia"}])
        self.assertEqual(master.unknown, {})


class PrimitivasCompartidas(unittest.TestCase):
    """Lo que los dos generadores tienen que entender igual."""

    def test_el_cero_es_un_hueco(self):
        for raw in (None, "", " ", "0", "-"):
            self.assertTrue(common.is_blank(raw), raw)
        self.assertFalse(common.is_blank("EMT-1"))

    def test_reconoce_las_hojas_de_trazado(self):
        self.assertTrue(common.is_track_sheet("HR Track 1"))
        self.assertTrue(common.is_track_sheet("HTrack 46"), "EP14A la escribe sin la R")
        self.assertTrue(common.is_track_sheet("HR Track 3(2)"))
        self.assertFalse(common.is_track_sheet("Recuento Conjuntos"))
        self.assertFalse(common.is_track_sheet("Legend"))

    def test_la_cabecera_esta_en_la_fila_2_o_en_la_3(self):
        titulo = ["TRACK 1"] + [None] * 15
        cabecera = ["a"] * 10 + [None] * 6
        self.assertEqual(common.find_header_row([titulo, cabecera, []]), 1)
        self.assertEqual(common.find_header_row([titulo, [None] * 16, cabecera]), 2)
        self.assertIsNone(common.find_header_row([titulo, [None] * 16, [None] * 16]))


@unittest.skipUnless(os.path.exists(MASTER), "data/profile-master.xlsx no generado")
class MaestroDePerfilesGenerado(unittest.TestCase):
    """Contraste sobre el fichero real, que es el que importara la aplicacion."""

    @classmethod
    def setUpClass(cls):
        try:
            import openpyxl
        except ImportError:
            raise unittest.SkipTest("openpyxl no instalado")

        wb = openpyxl.load_workbook(MASTER, read_only=True, data_only=True)
        try:
            def sheet(name):
                iterator = wb[name].iter_rows(values_only=True)
                header = list(next(iterator))
                return [dict(zip(header, row)) for row in iterator
                        if row and any(v is not None for v in row)]

            cls.tracks = sheet("TRACKS")
            cls.profiles = sheet("PROFILES")
            cls.cantilevers = sheet("CANTILEVERS")
            cls.unknown = sheet("NO_RECONOCIDO")
        finally:
            wb.close()

    # Huecos conocidos del maestro que hay hoy en data/. La lista NO es una excusa: es lo
    # que queda por cerrar, y tiene que llegar a cero.
    #
    # Son codigos sueltos que el catalogo no tiene. No queda ninguna familia: son casos
    # de uno en uno. Los huecos de topology.yml —estaciones sin declarar, hojas sin
    # declarar— ya estan todos cerrados.
    #
    # Lo que este test protege es que no aparezca NINGUNO NUEVO. Un valor sin reconocer que
    # no este aqui listado hace fallar el test, que es justo lo que se perdia si se dejaba
    # el assertEqual(..., []) 'temporalmente' comentado.
    HUECOS_CONOCIDOS = {
        # Soportes. La columna 'Supports' es un campo del perfil desde V20, asi que sus
        # codigos se comprueban contra el catalogo. Los nueve que describian un soporte
        # de verdad ya estan habilitados en track_accepted; estos cuatro no lo estan
        # porque NO son soportes:
        #   'SECT-I', 'FS1' y 'FS/PP2' son aparatos de 'Sectioning Feeding', y
        #   'B7' es un semiportico, codigo de Portal (plano 6127, 140 usos en su columna).
        # Los cuatro se corrigen en el workbook, no habilitandolos: el soporte sobre el
        # semiportico B7 ya tiene codigo propio, 'S1/B7', con 119 usos.
        ('codigo sin SupportType habilitado', 'B7'),
        ('codigo sin SupportType habilitado', 'FS/PP2'),
        ('codigo sin SupportType habilitado', 'FS1'),
        ('codigo sin SupportType habilitado', 'SECT-I'),

        # Anclaje. 'TRACK 5' es una anotacion de via, no un anclaje: quitandole la palabra
        # y el numero no queda codigo ninguno, asi que la celda sale nombrada en vez de
        # colarse en el catalogo como el codigo 'TRACK 5', que es lo que hacia antes.
        ('codigo sin Anchorage habilitado', 'TRACK 5'),

        # Codigos de otros catalogos que el origen escribe en la columna equivocada. Se
        # corrigen en el workbook: reencaminarlos aqui recuperaria el valor, pero dejaria
        # el Excel mal Y sin aviso. La fila entra igual, sin ese valor.
        #   'FP' es un anclaje y 'PHQ-1150' un brazo, los dos en 'Sectioning Feeding'.
        #   'MP-ISusp' es una suspension aislada —un SOPORTE— escrita en 'Portals'.
        #   'RW2 RW2T-C' son dos soportes de retorno en una celda, y la columna admite uno.
        ('codigo sin DisconnectorFunction habilitado', 'FP'),
        ('codigo sin DisconnectorFunction habilitado', 'PHQ-1150'),
        ('codigo sin Portal habilitado', 'MP-ISusp'),
        ('codigo sin ReturnSupport habilitado', 'RW2 RW2T-C'),

        # Seccionamiento. Casi todos son codigos de ANCHORAGE escritos en la columna
        # SECTIONNING: la leyenda declara AnRW (Return Anchor), AnFW (Feeder Anchor) e IO
        # (Insulated Overlap) en el otro catalogo. Se corrigen en el workbook, no aqui.
        # 'POLE TRACK 14S' es una anotacion y la 'T' de 'AnMP T A/S-Diag' no es nada, pero
        # 'AnMP' si es un anclaje, asi que esa celda tampoco se puede cargar entera.
        ('codigo sin Sectioning habilitado', 'A/S-Diag AnRW'),
        ('codigo sin Sectioning habilitado', 'AnFW AnFW'),
        ('codigo sin Sectioning habilitado', 'AnMP AnRw'),
        ('codigo sin Sectioning habilitado', 'AnMP T A/S-Diag'),
        ('codigo sin Sectioning habilitado', 'AnRW'),
        ('codigo sin Sectioning habilitado', 'AnRW/Tunnel'),
        ('codigo sin Sectioning habilitado', 'AnRW2'),
        ('codigo sin Sectioning habilitado', 'IO'),
        ('codigo sin Sectioning habilitado', 'P120(Tg) S/A IO'),
        ('codigo sin Sectioning habilitado', 'POLE TRACK 14S'),
        ('codigo sin Sectioning habilitado', 'S/A A/S AnRW1 AnRW2'),
        ('codigo sin Sectioning habilitado', 'S/A IO'),
        ('codigo sin Sectioning habilitado', 'S/A IO S/A'),
    }

    def test_no_aparece_ningun_hueco_nuevo(self):
        aparecidos = {(r["TIPO"], r["VALOR"]) for r in self.unknown}
        self.assertEqual(aparecidos - self.HUECOS_CONOCIDOS, set())

    def test_la_lista_de_huecos_conocidos_no_se_queda_obsoleta(self):
        """Un hueco ya cerrado tiene que salir de la lista, o deja de protegerse nada."""
        aparecidos = {(r["TIPO"], r["VALOR"]) for r in self.unknown}
        self.assertEqual(self.HUECOS_CONOCIDOS - aparecidos, set())

    def test_estan_las_174_vias_declaradas(self):
        # 177 hojas menos las 3 vacias, que se declaran con 'skip'. Las dos de EP9A que
        # llevan dos tramos concatenados cuentan como UNA via cada una desde V18.
        self.assertEqual(len(self.tracks), 174)

    def test_la_hoja_con_dos_tramos_sale_como_UNA_via(self):
        # Era al reves hasta V18: se partia en dos porque el identificador de perfil se
        # repite entre los dos tramos. Ahora la clave natural lleva el KP y no hace falta.
        ep9a = [t for t in self.tracks if t["EP"] == "EP9A" and t["HOJA_ORIGEN"] == "HR Track 1"]
        self.assertEqual(len(ep9a), 1)
        self.assertIsNone(ep9a[0]["FILA_INICIO"], "ya no se corta por filas")

    def test_ninguna_clave_natural_cargable_se_repite(self):
        # (via, profileId, KP) es la clave natural del perfil desde V18: dos filas
        # cargables con la misma clave chocarian contra ux_profile_track_profile_id_kp.
        # El identificador SOLO ya no vale como clave —una via con dos tramos lo repite a
        # proposito— y por eso el KP entra aqui.
        seen = set()
        for profile in self.profiles:
            if profile["ENABLED"] != "SI":
                continue
            key = (profile["EP"], profile["VIA"], str(profile["PROFILE_ID"]).upper(),
                   str(profile["KP"]))
            self.assertNotIn(key, seen, key)
            seen.add(key)

    def test_el_identificador_repetido_con_otro_kp_SI_se_carga(self):
        # Es lo que permite no partir la via. Sin esto, el test de arriba pasaria tambien
        # con el comportamiento viejo, que descartaba la segunda aparicion.
        ep9a = [p for p in self.profiles
                if p["EP"] == "EP9A" and p["VIA"] == "TRACK 1" and p["ENABLED"] == "SI"]
        self.assertTrue(ep9a)
        repetidos = {p["PROFILE_ID"] for p in ep9a
                     if sum(1 for q in ep9a if q["PROFILE_ID"] == p["PROFILE_ID"]) > 1}
        self.assertGreater(len(repetidos), 40,
                           "los dos tramos de EP9A repiten decenas de identificadores")

    def test_los_perfiles_de_una_via_van_numerados_en_orden(self):
        # ORDEN es lo que ordena los perfiles desde V18, porque el KP dejo de servir: el
        # segundo tramo reinicia la kilometracion.
        porvia = {}
        for p in self.profiles:
            porvia.setdefault((p["EP"], p["VIA"]), []).append(p["ORDEN"])
        for via, ordenes in porvia.items():
            self.assertEqual(ordenes, list(range(1, len(ordenes) + 1)), via)

    def test_una_clave_repetida_queda_sin_cargar_y_marcada(self):
        counts = {}
        for profile in self.profiles:
            key = (profile["EP"], profile["VIA"], str(profile["PROFILE_ID"]).upper(),
                   str(profile["KP"]))
            counts.setdefault(key, []).append(profile)

        repeated = [rows for rows in counts.values() if len(rows) > 1]
        self.assertTrue(repeated, "el origen trae al menos un identificador repetido")
        for rows in repeated:
            self.assertEqual(sum(1 for r in rows if r["ENABLED"] == "SI"), 1)
            self.assertTrue(all(r["REVISAR"] == "SI" for r in rows if r["ENABLED"] == "NO"))

    def test_la_hoja_desplazada_no_sale_corrida(self):
        # EP6 / HR Track 1 HER no tiene columna 'Sectionning'. Leida por indice, su
        # tipo de poste vendria de la columna equivocada.
        rows = [p for p in self.profiles if p["HOJA_ORIGEN"] == "HR Track 1 HER"]
        self.assertTrue(rows)
        self.assertTrue(all(not p["SECTIONING"] for p in rows))
        self.assertTrue(any(str(p["POLE_TYPE"]).startswith(("HEB", "2HEB")) for p in rows))

    def test_ningun_perfil_cargable_se_queda_sin_kp(self):
        # kilometric_point es NOT NULL: un perfil sin KP no se puede importar.
        self.assertTrue(all(p["KP"] is not None
                            for p in self.profiles if p["ENABLED"] == "SI"))

    def test_ninguna_mensula_cargable_se_queda_sin_tipo(self):
        self.assertTrue(all(c["CANTILEVER_TYPE"]
                            for c in self.cantilevers if c["ENABLED"] == "SI"))

    def test_ninguna_mensula_cargable_cuelga_de_un_perfil_que_no_carga(self):
        """Una mensula sin su perfil no llega a la base de datos, asi que no es cargable.

        El importador agrupa las mensulas por (EP, via, ORDEN) y solo escribe las del perfil
        que esta cargando. Cuatro mensulas decian ENABLED=SI colgando de perfiles que el
        maestro descarta —identificador y KP repetidos—, asi que el recuento de cargables
        prometia cuatro que no podian entrar.
        """
        cargables = {(p["EP"], p["VIA"], p["ORDEN"])
                     for p in self.profiles if p["ENABLED"] == "SI"}
        huerfanas = [c for c in self.cantilevers if c["ENABLED"] == "SI"
                     and (c["EP"], c["VIA"], c["ORDEN"]) not in cargables]
        self.assertEqual(huerfanas, [])

    def test_el_recuento_de_mensulas_del_maestro_es_el_esperado(self):
        """Un guardian de recuento, no una comprobacion de logica.

        Faltaba, y por eso una regla nueva pudo mover el total 217 mensulas sin que
        fallara ni un test. Si estas cifras cambian, cambialas A PROPOSITO y explica por
        que en el commit: son las que se cargan en base de datos.
        """
        self.assertEqual(len(self.cantilevers), 14461)
        self.assertEqual(sum(1 for c in self.cantilevers if c["ENABLED"] == "SI"), 14451)

    def test_el_perfil_lleva_su_tipo_de_soporte(self):
        """La columna 'Supports' es un campo del perfil, no una columna sin mapear.

        Estaba en NO_MAPEADO: se recogia y se quedaba ahi. Y no era inocuo, porque el
        generador YA la lee para deducir el tipo de mensula en catenaria rigida, asi que
        el dato decidia lo que se carga sin llegar a guardarse en ninguna parte.
        """
        con_soporte = [p for p in self.profiles if p["SUPPORT_TYPE"]]
        self.assertGreater(len(con_soporte), 1900)
        # Uno solo, nunca una lista: el origen no escribe dos codigos en esa celda.
        self.assertEqual([p for p in con_soporte if bpm.LOV_SEPARATOR in str(p["SUPPORT_TYPE"])], [])

    def test_la_columna_supports_ya_no_se_recoge_como_no_mapeada(self):
        """Si siguiera en NO_MAPEADO estaria en los dos sitios, y uno de los dos mentiria."""
        import openpyxl
        wb = openpyxl.load_workbook(MASTER, read_only=True, data_only=True)
        try:
            iterator = wb["NO_MAPEADO"].iter_rows(values_only=True)
            header = list(next(iterator))
            columna = header.index("COLUMNA")
            self.assertNotIn("Supports", {r[columna] for r in iterator if r})
        finally:
            wb.close()

    def test_toda_mensula_con_el_tipo_supuesto_esta_senalada(self):
        """El tipo no viene del origen, asi que la mensula no puede entrar como las demas.

        'UNKNOWN' es un marcador: dice que ahi hay una mensula y que nadie ha escrito de
        que tipo es. Sale siempre con REVISAR=SI para que se distinga de un tipo real.
        """
        supuestas = [c for c in self.cantilevers if c["CANTILEVER_TYPE"] == "UNKNOWN"]
        self.assertTrue(supuestas, "el marcador tiene que estar en uso")
        self.assertEqual([c for c in supuestas if c["REVISAR"] != "SI"], [])

    def test_un_perfil_no_lleva_mas_de_tres_mensulas(self):
        # Es el limite de la entidad y del origen.
        self.assertTrue(all(1 <= c["SLOT"] <= 3 for c in self.cantilevers))

    def test_toda_estacion_usada_por_una_via_esta_declarada(self):
        import openpyxl
        wb = openpyxl.load_workbook(MASTER, read_only=True, data_only=True)
        try:
            def sheet(name):
                iterator = wb[name].iter_rows(values_only=True)
                header = list(next(iterator))
                return [dict(zip(header, row)) for row in iterator
                        if row and any(v is not None for v in row)]

            declared = {(r["EP"], str(r["NOMBRE"]).upper()) for r in sheet("STATIONS")}
        finally:
            wb.close()

        # Desde V17 una via puede atravesar varias estaciones, separadas por barra vertical.
        usadas = {(t["EP"], nombre.strip().upper())
                  for t in self.tracks if t["ESTACIONES"]
                  for nombre in str(t["ESTACIONES"]).split("|") if nombre.strip()}

        self.assertEqual(usadas - declared, set())

    def test_los_tipos_de_brazo_son_los_del_catalogo(self):
        used = {c["STEADY_ARM_TYPE"] for c in self.cantilevers if c["STEADY_ARM_TYPE"]}
        self.assertTrue(used <= set(ARM_TYPES), used - set(ARM_TYPES))

    def test_las_longitudes_de_brazo_caben_en_la_columna(self):
        lengths = [c["STEADY_ARM_LENGTH"] for c in self.cantilevers
                   if c["STEADY_ARM_LENGTH"] is not None]
        self.assertTrue(lengths)
        self.assertTrue(all(1 <= n <= 2000 for n in lengths))


@unittest.skipUnless(os.path.exists(LOV_MASTER), "data/lov-master.xlsx no generado")
class CodigosDeListaDeValores(unittest.TestCase):
    """Canonicalizacion y cruce contra el catalogo.

    Es la comprobacion que faltaba y la que mas caro salia: MasterDataService resuelve un
    codigo desconocido a null SIN QUEJARSE, asi que un codigo que no este habilitado no da
    error en ninguna parte — el perfil se guarda con la clave ajena vacia y el informe dice
    que fue bien. Aqui se corta antes de escribir el maestro.
    """

    CFG_LOV = dict(CFG, code_canonical={"DisconnectorFunction": {"FW25": "FW-25"}},
                   lov_catalog={"DisconnectorFunction": {"FW-25": "FW-25"}, "PoleType": {"S1T": "S1T"}})

    def resolver(self, field, text, cfg=None):
        master = bpm.Master()
        value, _ = bpm.resolve_lov(field, text, cfg or self.CFG_LOV, "EP1", "H", 7, master)
        return value, master.unknown

    def test_un_alias_se_convierte_en_su_codigo_canonico(self):
        value, unknown = self.resolver("SECTIONING_FEEDING", "FW25")
        self.assertEqual(value, "FW-25")
        self.assertEqual(unknown, {})

    def test_el_alias_se_reconoce_sin_importar_mayusculas(self):
        value, _ = self.resolver("SECTIONING_FEEDING", "fw25")
        self.assertEqual(value, "FW-25")

    def test_un_codigo_ya_canonico_pasa_tal_cual(self):
        value, unknown = self.resolver("POLE_TYPE", "S1T")
        self.assertEqual(value, "S1T")
        self.assertEqual(unknown, {})

    def test_un_codigo_que_no_esta_habilitado_va_a_no_reconocido(self):
        value, unknown = self.resolver("POLE_TYPE", "NO_EXISTE")
        # Sale VACIO, no tal cual: escribirlo daba un maestro que el importador no puede
        # cargar —comprueba cada codigo contra su catalogo— y tumbaba la fila entera por
        # una relacion opcional. No se esconde: queda en NO_RECONOCIDO, con su fila.
        self.assertEqual(value, "")
        self.assertEqual(len(unknown), 1)
        item = next(iter(unknown.values()))
        self.assertIn("PoleType", item["tipo"])
        self.assertEqual(item["valor"], "NO_EXISTE")

    def test_un_hueco_no_se_comprueba(self):
        value, unknown = self.resolver("POLE_TYPE", "")
        self.assertEqual(value, "")
        self.assertEqual(unknown, {})

    def test_sin_catalogo_cargado_solo_canonicaliza(self):
        """Los tests de unidad construyen su CFG a mano; main() siempre carga el catalogo."""
        cfg = dict(CFG, code_canonical={"DisconnectorFunction": {"FW25": "FW-25"}})
        value, unknown = self.resolver("SECTIONING_FEEDING", "FW25", cfg)
        self.assertEqual(value, "FW-25")
        self.assertEqual(unknown, {})

    def test_el_seccionamiento_admite_VARIOS_codigos_en_una_celda(self):
        """Es la unica columna multivalor: un perfil de estacion lleva 'A/S' y 'P50' a la vez."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"A/S": "A/S", "P50(CS)": "P50(CS)"}})
        value, unknown = self.resolver("SECTIONING", "A/S P50(CS)", cfg)
        self.assertEqual(value, "A/S|P50(CS)")
        self.assertEqual(unknown, {})

    def test_A_S_Diag_es_UN_codigo_y_no_dos(self):
        """'A/S Diag' es 'A/S-Diag' escrito con espacio, no 'A/S' mas un 'Diag' inventado.

        Lo dice la leyenda de los workbooks: 'Diagonal Anchorage' es A/S-Diag, y un 'Diag'
        suelto no existe en SECTIONNING. La regla la aplica code_tokens, compartida con el
        generador del catalogo para que los dos partan igual.
        """
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"A/S": "A/S", "A/S-DIAG": "A/S-Diag"}})
        value, unknown = self.resolver("SECTIONING", "A/S Diag", cfg)
        self.assertEqual(value, "A/S-Diag")
        self.assertEqual(unknown, {})

    def test_una_celda_con_un_token_que_no_es_codigo_no_se_parte(self):
        """La condicion sigue siendo que TODAS las partes sean codigos.

        'AnMP T A/S-Diag' lleva una 'T' que nadie ha sabido identificar, asi que la celda
        no se reparte a medias: sale entera en NO_RECONOCIDO, con su hoja y su fila.
        """
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"Sectioning": {"ANMP": "AnMP", "A/S-DIAG": "A/S-Diag"}})
        value, unknown = self.resolver("SECTIONING", "AnMP T A/S-Diag", cfg)
        self.assertEqual(value, "")
        self.assertEqual(len(unknown), 1)

    def test_el_guion_suelto_separa_dos_codigos(self):
        """'A/S - S/A' son dos: Overlap Anchorage y Overlap Semi-Axis."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"A/S": "A/S", "S/A": "S/A"}})
        value, unknown = self.resolver("SECTIONING", "A/S - S/A", cfg)
        self.assertEqual(value, "A/S|S/A")
        self.assertEqual(unknown, {})

    def test_el_sufijo_de_via_no_es_parte_del_codigo(self):
        """'AnMP(T1)' es 'AnMP': el (T1) dice en que via esta, y eso ya lo sabe la via."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"ANMP": "AnMP"}})
        value, unknown = self.resolver("SECTIONING", "AnMP(T1)", cfg)
        self.assertEqual(value, "AnMP")
        self.assertEqual(unknown, {})

    def test_la_celda_entera_gana_a_la_particion(self):
        """Si la celda ES un codigo, se respeta aunque lleve espacio."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"A/S DIAG": "A/S DIAG", "A/S": "A/S"}})
        value, unknown = self.resolver("SECTIONING", "A/S DIAG", cfg)
        self.assertEqual(value, "A/S DIAG")
        self.assertEqual(unknown, {})

    def test_el_espacio_antes_del_parentesis_no_parte_el_codigo(self):
        """'P50 (CS) S/A' son DOS valores, no tres: la errata no puede romper 'P50(CS)'."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"P50(CS)": "P50(CS)", "S/A": "S/A"}})
        value, unknown = self.resolver("SECTIONING", "P50 (CS) S/A", cfg)
        self.assertEqual(value, "P50(CS)|S/A")
        self.assertEqual(unknown, {})

    def test_un_codigo_con_espacios_sale_entero_y_sin_separador(self):
        """'P30(CS) A/S Diag' es UN codigo del catalogo, no tres.

        Es lo que obliga a que el separador del maestro sea la barra y no el espacio: con
        el espacio, el importador partia este codigo en 'P30(CS)', 'A/S' y un 'Diag' que
        no existe, y se caian 142 perfiles.
        """
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"Sectioning": {"P30(CS) A/S DIAG": "P30(CS) A/S Diag",
                                               "A/S": "A/S"}})
        value, unknown = self.resolver("SECTIONING", "P30(CS) A/S Diag", cfg)
        self.assertEqual(value, "P30(CS) A/S Diag")
        self.assertNotIn(bpm.LOV_SEPARATOR, value)
        self.assertEqual(unknown, {})

    def test_el_mismo_seccionamiento_dos_veces_es_uno(self):
        cfg = dict(self.CFG_LOV, lov_catalog={"Sectioning": {"S/A": "S/A"}})
        value, _ = self.resolver("SECTIONING", "S/A S/A", cfg)
        self.assertEqual(value, "S/A")

    def test_el_anclaje_tambien_admite_varios(self):
        """'FP+AnMC CP+AnMC' es uno CON regulacion de tension y otro SIN ella.

        El catalogo va {CODIGO_EN_MAYUSCULAS: grafia real}, que es lo que devuelve
        load_lov_catalog. Se busca por la clave y se escribe LA GRAFIA, no lo que trajera
        el origen: findByCode distingue mayusculas, asi que 'FP+ANMC' no resolveria.
        """
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"Anchorage": {"FP+ANMC": "FP+AnMC", "CP+ANMC": "CP+AnMC"}})
        value, unknown = self.resolver("ANCHORAGE", "FP+AnMC CP+AnMC", cfg)
        self.assertEqual(value, "FP+AnMC|CP+AnMC")
        self.assertEqual(unknown, {})

    # --- ANCHORAGE: lo que la leyenda escribe al lado del codigo -------------------
    #
    # La columna ANCHORAGE no lleva solo anclajes. La leyenda declara ademas una
    # 'SEMI TENSION LENGTH xxx m.', y el origen la teclea pegada al codigo. Tambien se
    # cuelan la palabra Portal —que tiene columna propia— y anotaciones de via. Nada de
    # eso es un anclaje, y mientras se leia como tal cada variante entraba al catalogo
    # como un codigo mas: 'CP+AnMC 265,00' llego a ser una fila.

    CFG_ANC = None      # se construye en cada test, con el catalogo que ese test necesita

    def anclaje(self, text, catalogo, canonical=None):
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"Anchorage": catalogo},
                   code_canonical={"Anchorage": canonical or {}},
                   code_noise_tokens={"Anchorage": {
                       "exact": ["Portal", "Track", "T1"],
                       "regex": r"^\d+([.,]\d+)?$"}})
        return self.resolver("ANCHORAGE", text, cfg)

    def test_la_longitud_de_semitension_no_es_parte_del_anclaje(self):
        """'CP+AnMC 265,00' es un anclaje y una longitud, no un codigo llamado asi.

        El dominio no tiene donde guardar esa longitud, asi que se queda el anclaje y el
        numero se tira. Lo que no puede pasar es lo de antes: que la celda entera acabe
        siendo una fila del catalogo que solo casa con ese perfil.
        """
        value, unknown = self.anclaje("CP+AnMC 265,00", {"CP+ANMC": "CP+AnMC"})
        self.assertEqual(value, "CP+AnMC")
        self.assertEqual(unknown, {})

    def test_el_mismo_anclaje_repetido_con_su_longitud_sigue_siendo_uno(self):
        value, _ = self.anclaje("CP+AnMC CP+AnMC 527,00", {"CP+ANMC": "CP+AnMC"})
        self.assertEqual(value, "CP+AnMC")

    def test_Portal_no_es_un_anclaje_sino_una_fuga_de_su_columna(self):
        """'AnRW Portal' es un AnRW. 'Portal' es otro catalogo y tiene su propia columna."""
        value, unknown = self.anclaje("AnRW Portal", {"ANRW": "AnRW"})
        self.assertEqual(value, "AnRW")
        self.assertEqual(unknown, {})

    def test_la_anotacion_de_via_no_es_parte_del_anclaje(self):
        """'AnMP (Track 02)' y 'AnRW2 track 1' son el anclaje: la via ya la sabe la via.

        Es la misma regla que el '(T1)' del seccionamiento, escrita con la palabra entera.
        """
        self.assertEqual(self.anclaje("AnMP (Track 02)", {"ANMP": "AnMP"})[0], "AnMP")
        self.assertEqual(self.anclaje("AnRW2 track 1", {"ANRW2": "AnRW2"})[0], "AnRW2")

    def test_AnRW2_es_un_anclaje_DISTINTO_de_AnRW(self):
        """El numero forma parte del codigo; no es la cuenta de anclajes.

        Importa porque Profile.anchorages es un conjunto: si AnRW2 fuera 'dos AnRW' no
        habria donde guardar el dos, y 108 perfiles perderian la mitad del dato sin que
        se notara. Como son codigos distintos, cada uno es su propia fila del catalogo.
        """
        catalogo = {"ANRW": "AnRW", "ANRW2": "AnRW2"}
        self.assertEqual(self.anclaje("AnRW2", catalogo)[0], "AnRW2")
        self.assertEqual(self.anclaje("AnRW AnRW", catalogo)[0], "AnRW")   # repetido: uno
        self.assertEqual(self.anclaje("AnRW AnRW2", catalogo)[0], "AnRW|AnRW2")

    def test_dos_anclajes_pegados_sin_espacio_se_separan_por_la_tabla_de_grafias(self):
        """'FP+AnMCAnRW' son dos. Sin la tabla no hay forma de saber donde parte.

        Y la tabla tiene que aplicarse ANTES de trocear: troceando el texto original, el
        arreglo no llegaria a usarse nunca y la celda saldria sin resolver.
        """
        value, unknown = self.anclaje("FP+AnMCAnRW",
                                      {"FP+ANMC": "FP+AnMC", "ANRW": "AnRW"},
                                      canonical={"FP+AnMCAnRW": "FP+AnMC AnRW"})
        self.assertEqual(value, "FP+AnMC|AnRW")
        self.assertEqual(unknown, {})

    def test_las_erratas_de_tecleo_del_anclaje_se_canonicalizan(self):
        """'PF+AnMC' es 'FP+AnMC' con las letras cambiadas, no un anclaje nuevo."""
        value, _ = self.anclaje("PF+AnMC", {"FP+ANMC": "FP+AnMC"},
                                canonical={"PF+AnMC": "FP+AnMC"})
        self.assertEqual(value, "FP+AnMC")

    def test_las_variantes_de_longitud_de_CP_TX_son_anclajes_distintos(self):
        """CP/TX-P y CP/TX-P/1100 son dos anclajes, no uno con una medida al lado.

        Por eso el numero de CP/TX-* NO se trata como ruido: ahi va pegado con barra y
        forma parte del codigo, mientras que la longitud de semitension va suelta.
        """
        catalogo = {"CP/TX-P": "CP/TX-P", "CP/TX-P/1100": "CP/TX-P/1100"}
        self.assertEqual(self.anclaje("CP/TX-P", catalogo)[0], "CP/TX-P")
        self.assertEqual(self.anclaje("CP/TX-P/1100", catalogo)[0], "CP/TX-P/1100")

    def test_una_celda_que_solo_lleva_la_via_no_es_un_anclaje(self):
        """'TRACK 5' es una anotacion: no queda codigo, y la celda sale nombrada.

        Sale vacia y en NO_RECONOCIDO, que es lo contrario de desaparecer en silencio:
        es una celda que alguien tiene que mirar en el workbook.
        """
        value, unknown = self.anclaje("TRACK 5", {"ANRW": "AnRW"})
        self.assertEqual(value, "")
        self.assertEqual(len(unknown), 1)

    def test_el_aparato_de_seccionamiento_tambien_admite_varios(self):
        """'Disc SECT-I' es un disconnector MAS un aislador de seccion."""
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"DisconnectorFunction": {"DISC": "Disc", "SECT-I": "SECT-I"}})
        value, unknown = self.resolver("SECTIONING_FEEDING", "Disc SECT-I", cfg)
        self.assertEqual(value, "Disc|SECT-I")
        self.assertEqual(unknown, {})

    def test_las_columnas_de_una_sola_LOV_NO_admiten_varios(self):
        """En return_support dos codigos en una celda siguen siendo una anomalia."""
        cfg = dict(self.CFG_LOV, lov_catalog={"ReturnSupport": {"RW2": "RW2", "RW2T-C": "RW2T-C"}})
        value, unknown = self.resolver("RETURN_SUPPORT", "RW2 RW2T-C", cfg)
        self.assertEqual(value, "")
        self.assertEqual(len(unknown), 1)

    def test_un_codigo_repetido_en_la_celda_se_colapsa_en_uno(self):
        """'AnM-R AnM-R' no son dos valores: es uno escrito dos veces."""
        cfg = dict(self.CFG_LOV, lov_catalog={"PoleType": {"S1T": "S1T"}})
        value, unknown = self.resolver("POLE_TYPE", "S1T S1T", cfg)
        self.assertEqual(value, "S1T")
        self.assertEqual(unknown, {})

    def test_dos_codigos_DISTINTOS_no_se_eligen_solos(self):
        """Quedarse con uno seria decidir por el humano cual de los dos vale.

        POLE_TYPE no es multivalor: la columna admite un codigo y aqui vienen dos. Sale
        vacia y anotada en NO_RECONOCIDO, que es lo que pone la decision delante de quien
        puede tomarla, en vez de elegir por el o de tumbar el perfil al importar.
        """
        cfg = dict(self.CFG_LOV, lov_catalog={"PoleType": {"S1T": "S1T", "S2T": "S2T"}})
        value, unknown = self.resolver("POLE_TYPE", "S1T S2T", cfg)
        self.assertEqual(value, "")
        self.assertEqual(len(unknown), 1)
        self.assertEqual(next(iter(unknown.values()))["valor"], "S1T S2T")

    def test_un_codigo_con_espacio_no_se_parte(self):
        """'T-SIGN FOUND.' lleva espacio y es un codigo entero."""
        cfg = dict(self.CFG_LOV, lov_catalog={"Foundation": {"T-SIGN FOUND.": "T-SIGN FOUND."}})
        value, unknown = self.resolver("FOUNDATION", "T-SIGN FOUND.", cfg)
        self.assertEqual(value, "T-SIGN FOUND.")
        self.assertEqual(unknown, {})

    def test_se_escribe_la_grafia_del_catalogo_no_la_del_origen(self):
        """El importador resuelve con findByCode, que DISTINGUE mayusculas.

        Si el maestro llevara 'DISC/IO-pr' porque asi venia en el workbook, el generador lo
        daria por bueno —compara sin distinguir— y el importador rechazaria la fila. Los dos
        candados tienen que decir lo mismo.
        """
        cfg = dict(self.CFG_LOV,
                   lov_catalog={"DisconnectorFunction": {"DISC/IO-PR": "Disc/IO-pr"}})
        value, unknown = self.resolver("SECTIONING_FEEDING", "DISC/IO-pr", cfg)
        self.assertEqual(value, "Disc/IO-pr")
        self.assertEqual(unknown, {})

    def test_un_marcador_de_no_definido_sale_como_hueco_y_no_como_codigo(self):
        """'NON DEFINED' no es un codigo que falte por declarar: es la forma de escribir
        que ahi no hay dato. Sacarlo en NO_RECONOCIDO diria lo contrario de lo que pasa."""
        cfg = dict(self.CFG_LOV, code_rejections=["NON DEFINED", "N.D."])
        for marcador in ("NON DEFINED", "non defined", "N.D."):
            value, unknown = self.resolver("FOUNDATION", marcador, cfg)
            self.assertEqual(value, "", marcador)
            self.assertEqual(unknown, {}, marcador)

    def test_una_columna_que_no_es_lov_no_se_toca(self):
        value, unknown = self.resolver("KP", "12+345")
        self.assertEqual(value, "12+345")
        self.assertEqual(unknown, {})

    def test_la_canonicalizacion_se_aplica_al_leer_la_hoja(self):
        """El fallo real era este: la tabla existia y solo la aplicaba el generador de LOV."""
        cfg = dict(CFG, code_canonical={"PoleType": {"S1T-VIEJO": "S1T"}},
                   lov_catalog={"PoleType": {"S1T": "S1T"}})
        sheet = FakeSheet("HR Track 1", [
            pad(["Hoja"]),
            pad(HEADER),
            pad([None, "83-1.02", "1+000", None, None, "S1T-VIEJO"]),
        ])
        master = bpm.Master()
        bpm.read_track(sheet, "EP1", {"name": "VIA 1"}, cfg, master)

        self.assertEqual(master.profiles[0]["POLE_TYPE"], "S1T")
        self.assertEqual(master.unknown, {})


class CoherenciaConElCatalogoDeLov(unittest.TestCase):
    """Los tipos de brazo se declaran en aliases.yml y viven en lov-master.xlsx.

    Son dos ficheros, asi que pueden separarse. Aqui es donde se nota.
    """

    def test_los_tipos_de_brazo_declarados_son_los_habilitados_del_catalogo(self):
        try:
            import openpyxl
            import yaml
        except ImportError:
            self.skipTest("openpyxl/pyyaml no instalados")

        with open(os.path.join(BASE, "aliases.yml"), encoding="utf-8") as handle:
            declared = set(yaml.safe_load(handle)["steady_arm_types"])

        wb = openpyxl.load_workbook(LOV_MASTER, read_only=True, data_only=True)
        try:
            iterator = wb["LOVS"].iter_rows(values_only=True)
            header = list(next(iterator))
            enabled_column = header.index("ENABLED")
            catalogue = {str(row[1]) for row in iterator
                         if row and row[0] == "SteadyArmType" and row[enabled_column] == "SI"}
        finally:
            wb.close()

        self.assertEqual(declared, catalogue)

if __name__ == "__main__":
    unittest.main()


class CoberturaDeLosTramosDeclarados(unittest.TestCase):
    """Una hoja partida en tramos tiene que quedar cubierta ENTERA, y una sola vez.

    Partir una hoja con 'rows' es como se declara que un trozo de via esta dentro de
    una estacion y el siguiente ya no. El riesgo es aritmetico: si los rangos no
    cubren toda la hoja, las filas de en medio no salen en el maestro y el maestro
    cuadra consigo mismo, asi que nadie se entera hasta que alguien echa de menos un
    poste en obra.
    """

    @staticmethod
    def hoja():
        # Perfiles en las filas 4, 6 y 8; las intermedias llevan el vano.
        filas = [["TRACK 1"], HEADER, [None] * len(HEADER)]
        for numero in ("30-1.15", "30-1.16", "30-1.17"):
            filas.append([None, numero, 30675, None, "A/S", "S1T", "EMT-2T",
                          None, None, 20, None, None, "PH-950"])
            filas.append([None, None, None, 45.5])
        return FakeSheet("HR Track 1", [pad(r) for r in filas])

    def comprueba(self, tramos):
        master = bpm.Master()
        sheet = self.hoja()
        layout = bpm.track_layout(sheet, "EP1", CFG, master)
        declaraciones = [{"name": f"VIA {i}", "rows": r} for i, r in enumerate(tramos, 1)]
        bpm.check_sheet_coverage(layout, "EP1", declaraciones, master)
        return master

    def test_los_tramos_que_cubren_toda_la_hoja_no_dicen_nada(self):
        self.assertEqual(len(self.comprueba([[4, 5], [6, 9]]).unknown), 0)

    def test_una_fila_con_perfil_fuera_de_todo_tramo_no_pasa_en_silencio(self):
        master = self.comprueba([[4, 5], [8, 9]])          # la 6 se queda fuera
        tipos = [k[0] for k in master.unknown]
        self.assertIn("filas con perfil fuera de los tramos declarados", tipos)
        self.assertEqual(next(iter(master.unknown.values()))["valor"], "6")

    def test_dos_tramos_que_se_pisan_cargarian_el_perfil_dos_veces(self):
        master = self.comprueba([[4, 9], [6, 9]])
        tipos = [k[0] for k in master.unknown]
        self.assertIn("filas con perfil en dos tramos a la vez", tipos)

    def test_una_hoja_sin_partir_no_se_comprueba(self):
        """Sin 'rows' el tramo es la hoja entera: no hay nada que cubrir."""
        master = bpm.Master()
        layout = bpm.track_layout(self.hoja(), "EP1", CFG, master)
        bpm.check_sheet_coverage(layout, "EP1", [{"name": "VIA 1"}], master)
        self.assertEqual(len(master.unknown), 0)

    def test_las_filas_perdidas_se_resumen_en_rangos(self):
        """Un listado de 79 numeros sueltos no lo lee nadie."""
        self.assertEqual(bpm.ranges_text([121, 122, 123, 130, 131, 200]),
                         "121-123, 130-131, 200")

    def test_el_plano_de_la_hoja_se_resuelve_una_sola_vez(self):
        """Antes se resolvia una vez por tramo: la hoja de EP9A, dos veces."""
        master = bpm.Master()
        layout = bpm.track_layout(self.hoja(), "EP1", CFG, master)
        self.assertEqual(layout.sheet, "HR Track 1")
        self.assertEqual(bpm.profile_rows(layout), [4, 6, 8])


class EstacionesDeUnaVia(unittest.TestCase):
    """Una via larga atraviesa varias estaciones sin dejar de ser una via.

    'TRACK 1' de EP4 pasa por ZIC, por BIN y por HAD. Se declaran en plural, y el
    singular se sigue admitiendo porque las 124 vias ya rellenas con 'station:' no
    tienen por que reescribirse.
    """

    def test_el_singular_sigue_valiendo(self):
        self.assertEqual(bpm.track_stations({"station": "MOM"}), ["MOM"])

    def test_el_plural_las_devuelve_todas_y_en_orden(self):
        self.assertEqual(bpm.track_stations({"stations": ["ZIC", "BIN", "HAD"]}),
                         ["ZIC", "BIN", "HAD"])

    def test_singular_y_plural_a_la_vez_no_duplican(self):
        self.assertEqual(bpm.track_stations({"station": "ZIC", "stations": ["zic", "BIN"]}),
                         ["ZIC", "BIN"])

    def test_sin_estacion_es_una_respuesta_valida(self):
        self.assertEqual(bpm.track_stations({"station": None}), [])
        self.assertEqual(bpm.track_stations({}), [])

    def test_una_estacion_no_declarada_en_el_paquete_no_pasa_en_silencio(self):
        master = bpm.Master()
        bpm.check_declared_stations("EP4", {
            "stations": ["ZIC", "BIN"],
            "tracks": [{"sheet": "HR Track 1", "stations": ["ZIC", "BIN", "HAD"]}],
        }, master)
        self.assertEqual([k[1] for k in master.unknown], ["HAD"])


class ClaveNaturalYOrdenDeLaVia(unittest.TestCase):
    """Una via puede llevar dos tramos concatenados sin partirse en dos.

    'HR Track 1' de EP9A lleva dos tramos con la kilometracion reiniciada, y cada uno
    se numero por su cuenta: '5-1.01' existe en los dos. Antes eso obligaba a partir la
    hoja en dos vias con 'rows'. Desde V18 la clave natural incluye el KP, que es lo que
    distingue un mastil del otro, y el orden lo lleva una columna propia porque ordenar
    por KP mezclaria los dos tramos en vez de ponerlos uno detras del otro.
    """

    @staticmethod
    def hoja_con_dos_tramos():
        filas = [["TRACK 1"], HEADER, [None] * len(HEADER)]
        # tramo 1: kp 5421 y 5481
        for numero, kp in (("5-1.01", 5421), ("5-1.02", 5481)):
            filas.append([None, numero, kp, None, "A/S", "S1T", "EMT-2T",
                          None, None, 20, None, None, "PH-950"])
            filas.append([None, None, None, 45.5])
        # tramo 2: la kilometracion REINICIA y repite los identificadores
        for numero, kp in (("5-1.01", 270), ("5-1.02", 330)):
            filas.append([None, numero, kp, None, "A/S", "S1T", "EMT-2T",
                          None, None, 20, None, None, "PH-950"])
            filas.append([None, None, None, 45.5])
        return FakeSheet("HR Track 1", [pad(r) for r in filas])

    def lee(self):
        master = bpm.Master()
        bpm.read_track(self.hoja_con_dos_tramos(), "EP9A", {"name": "TRACK 1"}, CFG, master)
        return master

    def test_el_identificador_repetido_con_otro_kp_se_carga(self):
        # Es el cambio entero: antes la segunda aparicion se descartaba por repetida y por
        # eso habia que partir la hoja en dos vias.
        master = self.lee()
        cargables = [p for p in master.profiles if p["ENABLED"] == "SI"]
        self.assertEqual(len(cargables), 4)
        self.assertEqual([p["PROFILE_ID"] for p in cargables],
                         ["5-1.01", "5-1.02", "5-1.01", "5-1.02"])

    def test_el_orden_sigue_a_la_hoja_y_no_al_kp(self):
        # Ordenar por KP pondria el segundo tramo (270, 330) DELANTE del primero.
        master = self.lee()
        self.assertEqual([p["ORDEN"] for p in master.profiles], [1, 2, 3, 4])
        self.assertEqual([p["KP"] for p in master.profiles], [5421, 5481, 270, 330])

    def test_repetir_identificador_Y_kp_sigue_siendo_un_error(self):
        # Eso ya no es un tramo nuevo: son dos filas para el mismo mastil.
        filas = [["TRACK 1"], HEADER, [None] * len(HEADER)]
        for _ in range(2):
            filas.append([None, "8-1.12", 8447, None, "A/S", "S1T", "EMT-2T",
                          None, None, 20, None, None, "PH-950"])
            filas.append([None, None, None, 45.5])
        master = bpm.Master()
        bpm.read_track(FakeSheet("HR Track 1", [pad(r) for r in filas]),
                       "EP9A", {"name": "TRACK 1"}, CFG, master)
        self.assertEqual([p["ENABLED"] for p in master.profiles], ["SI", "NO"])
        self.assertTrue(any("repetidos" in d["motivo"] for d in master.discarded))
