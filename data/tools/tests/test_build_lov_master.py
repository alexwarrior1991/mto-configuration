"""Reglas de mapeo del generador del catalogo maestro de LOVs.

Se prueban las funciones puras contra un `aliases.yml` construido en el propio test,
no contra el real: asi un alias nuevo en produccion no rompe estas pruebas, y lo que
se comprueba es el MECANISMO, que es lo que puede romperse al tocar el script.

La ultima clase es la excepcion: contrasta el `data/lov-master.xlsx` ya generado, que
es el fichero que importa la aplicacion. Se salta sola si no esta.

    python3 -m unittest discover -s data/tools/tests
"""

import importlib.util
import os
import unittest

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(os.path.dirname(BASE), "lov-master.xlsx")

_spec = importlib.util.spec_from_file_location(
    "build_lov_master", os.path.join(BASE, "build_lov_master.py"))
blm = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(blm)


CFG = {
    "code_canonical": {
        "DisconnectorFunction": {
            "FW25": "FW-25",
            "FW+25": "FW-25",
            "F-25": "FW-25",
            "2-25FW": "2xFW-25",
            "LoadB/NZ": "LoadB/NS",
            "DISC/NZ": "Disc/NS",
            "SECT-1": "SECT-I",
        },
    },
    "track_accepted": {
        "DisconnectorFunction": ["FW", "FW-25", "LoadB/t"],
    },
    "code_reassignment": [
        {"from": "DisconnectorFunction",
         "regex": r"^(DISC SECT-I|LOADB/NS FS-1)$", "to": None},
        {"from": "DisconnectorFunction", "regex": r"^(PHQ-\d+|FP)$", "to": None},
        {"from": "Foundation", "regex": "^ANMC?/", "to": "AnchorageFoundation"},
    ],
    "code_rejections": ["N.D."],
}


class FormaCanonicaDeUnCodigo(unittest.TestCase):
    """Varias grafias del mismo equipo tienen que acabar en una sola fila.

    Si no, los usos se reparten entre variantes y cada una queda por debajo del
    umbral de atencion: 'FW-25' con 99 usos se ve, pero partido en 99/14/11/2 no.
    """

    def canonical(self, code, entity="DisconnectorFunction"):
        return blm.canonical_code(entity, code, CFG)

    def test_la_familia_fw_colapsa_en_dos_codigos(self):
        for grafia in ("FW25", "FW+25", "F-25"):
            self.assertEqual(self.canonical(grafia), "FW-25", grafia)
        self.assertEqual(self.canonical("2-25FW"), "2xFW-25")

    def test_nz_y_ns_son_el_mismo_equipo(self):
        # La leyenda de EP9B llama "N.Z. Disconnector" justo al codigo Disc/NS.
        self.assertEqual(self.canonical("LoadB/NZ"), "LoadB/NS")
        self.assertEqual(self.canonical("DISC/NZ"), "Disc/NS")

    def test_la_busqueda_ignora_mayusculas(self):
        # La clave del catalogo ya es insensible a mayusculas; la tabla de alias
        # tiene que serlo tambien o dependeria de como se teclease la celda.
        self.assertEqual(self.canonical("fw25"), "FW-25")
        self.assertEqual(self.canonical("loadb/nz"), "LoadB/NS")

    def test_un_codigo_que_no_esta_en_la_tabla_se_deja_igual(self):
        self.assertEqual(self.canonical("Disc/IO"), "Disc/IO")

    def test_la_tabla_es_por_entidad(self):
        # 'SECT-1' solo es una errata dentro de DisconnectorFunction.
        self.assertEqual(self.canonical("SECT-1"), "SECT-I")
        self.assertEqual(self.canonical("SECT-1", entity="Foundation"), "SECT-1")

    def test_una_entidad_sin_tabla_no_falla(self):
        self.assertEqual(self.canonical("LO QUE SEA", entity="PoleType"), "LO QUE SEA")


class AceptacionDeCodigosDeHojaTrack(unittest.TestCase):
    """La decision de aceptar un codigo de trazado vive en aliases.yml.

    Aceptarlo editando el Excel generado no vale: el maestro se regenera y se lleva
    por delante cualquier ENABLED=SI puesto a mano.
    """

    def accepted(self, code, entity="DisconnectorFunction"):
        return blm.is_track_accepted(entity, code, CFG)

    def test_un_codigo_aceptado_lo_esta(self):
        self.assertTrue(self.accepted("FW"))
        self.assertTrue(self.accepted("LoadB/t"))

    def test_un_codigo_no_aceptado_no_lo_esta(self):
        self.assertFalse(self.accepted("Disc/IO-pr"))

    def test_ignora_mayusculas(self):
        self.assertTrue(self.accepted("loadb/T"))

    def test_una_entidad_sin_lista_no_acepta_nada(self):
        self.assertFalse(self.accepted("FW", entity="PoleType"))


class DescarteDeCeldasQueNoSonUnCodigo(unittest.TestCase):
    """'Sectioning Feeding' es una clave ajena y no puede con dos valores."""

    def route(self, code, entity="DisconnectorFunction"):
        return blm.route_code(entity, code, CFG)

    def test_una_celda_con_dos_equipos_se_descarta_con_motivo(self):
        entity, reason = self.route("Disc SECT-I")
        self.assertIsNone(entity)
        self.assertIn("fuera de catalogo", reason)

    def test_una_fuga_de_otra_columna_se_descarta(self):
        # PHQ-1150 es un brazo y FP un anclaje: se han tecleado en la columna
        # equivocada. Descartarlos con motivo, no cargarlos como alimentacion.
        self.assertIsNone(self.route("PHQ-1150")[0])
        self.assertIsNone(self.route("FP")[0])

    def test_un_codigo_normal_pasa(self):
        self.assertEqual(self.route("Disc/IO"), ("DisconnectorFunction", None))

    def test_las_reglas_son_por_entidad(self):
        # La misma cadena en otra entidad no se toca: la regla dice 'from'.
        self.assertEqual(self.route("FP", entity="Anchorage")[0], "Anchorage")

    def test_un_marcador_sin_valor_se_descarta(self):
        entity, reason = self.route("N.D.")
        self.assertIsNone(entity)
        self.assertIn("marcador", reason)


class DecisionDeEnabledYRevisar(unittest.TestCase):
    """Dos motivos independientes piden revision humana; se comprueban por separado."""

    @staticmethod
    def row(code, sources, entity="DisconnectorFunction"):
        return {"entity": entity, "code": code, "sources": set(sources), "type": ""}

    def test_un_codigo_de_catalogo_curado_entra_sin_revision(self):
        row = self.row("Disc/IO", {"LEGEND", "TRACK"})
        self.assertTrue(blm.decide_enabled(row, CFG))
        self.assertTrue(row["enabled"])
        self.assertFalse(row["revisar"])

    def test_un_codigo_solo_de_track_sin_aceptar_pide_revision(self):
        row = self.row("Disc/IO-pr", {"TRACK"})
        blm.decide_enabled(row, CFG)
        self.assertFalse(row["enabled"])
        self.assertTrue(row["revisar"])

    def test_un_codigo_solo_de_track_ya_aceptado_entra(self):
        row = self.row("FW", {"TRACK"})
        blm.decide_enabled(row, CFG)
        self.assertTrue(row["enabled"])
        self.assertFalse(row["revisar"])

    def test_una_entidad_con_tipo_obligatorio_sin_resolver_pide_revision(self):
        # Foundation exige FoundationType. Sin reglas de tipo no se puede deducir,
        # y cargarlo dejaria una relacion obligatoria a null.
        row = self.row("XXXX", {"BOQ"}, entity="Foundation")
        self.assertFalse(blm.decide_enabled(row, CFG))
        self.assertTrue(row["revisar"])
        self.assertFalse(row["enabled"])


class AgregacionEnElCatalogo(unittest.TestCase):
    """El recorrido completo de una fila: canonicalizar, enrutar y fusionar."""

    def test_dos_grafias_del_mismo_codigo_suman_sus_usos_en_una_fila(self):
        cat = blm.Catalogue(CFG)
        cat.add("DisconnectorFunction", "FW-25", source="TRACK", ep="EP4", track_uses=99)
        cat.add("DisconnectorFunction", "FW25", source="TRACK", ep="EP6", track_uses=14)
        cat.add("DisconnectorFunction", "F-25", source="TRACK", ep="EP6", track_uses=2)

        self.assertEqual(len(cat.rows), 1)
        row = next(iter(cat.rows.values()))
        self.assertEqual(row["code"], "FW-25")
        self.assertEqual(row["track_uses"], 115)
        self.assertEqual(row["eps"], {"EP4", "EP6"})

    def test_lo_descartado_no_entra_en_el_catalogo_pero_queda_registrado(self):
        cat = blm.Catalogue(CFG)
        cat.add("DisconnectorFunction", "Disc SECT-I", source="TRACK", ep="EP7", track_uses=4)

        self.assertEqual(cat.rows, {})
        self.assertEqual(len(cat.discarded), 1)
        self.assertEqual(cat.discarded[0]["codigo"], "Disc SECT-I")

    def test_la_canonicalizacion_va_despues_del_enrutado(self):
        # El alias se busca en la entidad de DESTINO, que es la que decide route_code.
        cat = blm.Catalogue(CFG)
        cat.add("Foundation", "AnM/PL", source="BOQ", ep="EP4")

        row = next(iter(cat.rows.values()))
        self.assertEqual(row["entity"], "AnchorageFoundation")


@unittest.skipUnless(os.path.exists(MASTER), "data/lov-master.xlsx no generado")
class MaestroGenerado(unittest.TestCase):
    """Contraste sobre el fichero real, que es el que importa la aplicacion.

    Cubre lo que las pruebas de mecanismo no ven: que el aliases.yml de produccion
    produce el catalogo que se espera.
    """

    @classmethod
    def setUpClass(cls):
        try:
            import openpyxl
        except ImportError:
            raise unittest.SkipTest("openpyxl no instalado")

        wb = openpyxl.load_workbook(MASTER, read_only=True, data_only=True)
        try:
            sheet = wb["LOVS"]
            header = [str(c) if c else "" for c in
                      next(sheet.iter_rows(min_row=1, max_row=1, values_only=True))]
            index = {name: n for n, name in enumerate(header)}
            cls.feeding = {
                str(r[1]): r[index["ENABLED"]]
                for r in sheet.iter_rows(min_row=2, values_only=True)
                if r and r[0] == "DisconnectorFunction"
            }
            cls.unknown_rows = sum(
                1 for r in wb["NO_RECONOCIDO"].iter_rows(min_row=2, values_only=True)
                if r and any(v is not None for v in r))
        finally:
            wb.close()

    def test_los_22_codigos_del_bloque_feeding_estan_habilitados(self):
        # Son los que define la leyenda, identica en los cinco workbooks que la traen.
        legend = {
            "Disc", "Disc/NS", "Disc/IO", "Disc/SI", "Disc/t",
            "LoadB", "LoadB/NS", "LoadB/IO", "LoadB/PP",
            "ED", "ED/T", "SurgeA", "VoltageD", "CurrentT", "SECT-I",
            "FS-1", "FS-1D", "FS/PP-2", "FS/PP-3", "PP-2", "PP-3", "PP-4",
        }
        faltan = {c for c in legend if self.feeding.get(c) != "SI"}
        self.assertEqual(faltan, set())

    def test_la_familia_fw_esta_habilitada_y_canonicalizada(self):
        for code in ("FW", "FW-25", "FW-D", "FW-DV", "2xFW-25"):
            self.assertEqual(self.feeding.get(code), "SI", code)
        for grafia in ("FW25", "FW+25", "F-25", "2-25FW"):
            self.assertNotIn(grafia, self.feeding, grafia)

    def test_las_variantes_nz_se_han_fundido_con_ns(self):
        self.assertNotIn("LoadB/NZ", self.feeding)
        self.assertNotIn("DISC/NZ", self.feeding)

    def test_los_sufijos_pr_y_pp_siguen_pendientes_de_decidir(self):
        # No se habilitan hasta saber que significan; quedan a la vista con
        # ENABLED=NO en lugar de perderse en silencio.
        for code in ("Disc-pr", "Disc-pp", "Disc/IO-pr", "Disc/PP-pr",
                     "Disc/SI-pr", "LoadB-pr", "LoadB-pp"):
            self.assertEqual(self.feeding.get(code), "NO", code)

    def test_las_celdas_con_dos_valores_no_estan_en_el_catalogo(self):
        for code in ("Disc SECT-I", "FS-1 VoltageD", "LoadB/NS FS-1", "LoadB/PP FS-1"):
            self.assertNotIn(code, self.feeding, code)

    def test_no_queda_nada_sin_reconocer(self):
        # Si esta hoja trae filas, el catalogo sale incompleto y el generador
        # termina con codigo != 0.
        self.assertEqual(self.unknown_rows, 0)


if __name__ == "__main__":
    unittest.main()
