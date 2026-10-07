"""Reglas de lectura del plano de seccionamiento que no necesitan el DXF.

Lo que se prueba aqui es lo que decide un dato del libro de revision a partir de un texto o de
unas coordenadas: como se lee un KP, cuantos aparatos nombra un rotulo, que es un rotulo de
subestacion y no un seccionador, cuando un simbolo esta en la columna de su rotulo y como se
elige la via del maestro. La lectura del DXF y la asignacion (ezdxf, scipy) quedan fuera: CI
instala solo openpyxl y pyyaml, y el modulo las importa dentro de las funciones que las usan.

    python3 -m unittest discover -s data/tools/tests
"""

import importlib.util
import os
import unittest

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(BASE, name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bsr = _load("build_sectioning_review")


class KpTest(unittest.TestCase):

    def test_the_plan_writes_km_plus_metres_and_the_master_wants_metres(self):
        self.assertEqual(bsr.parse_kp("KP93+451"), ("KP93+451", 93451))
        self.assertEqual(bsr.parse_kp("KP 13+523"), ("KP13+523", 13523))
        self.assertEqual(bsr.parse_kp("K.P.\t1+915"), ("KP1+915", 1915))
        self.assertEqual(bsr.parse_kp("(KP101+200)"), ("KP101+200", 101200))

    def test_a_bare_kp_and_a_decimal_kp_are_read_too(self):
        self.assertEqual(bsr.parse_kp("131+200"), ("KP131+200", 131200))
        self.assertEqual(bsr.parse_kp("KP9+320.5"), ("KP9+320.5", 9320.5))

    def test_an_x_in_the_kp_is_an_unknown_kp_not_a_zero(self):
        self.assertEqual(bsr.parse_kp("KPXX+XXX"), ("KPXX+XXX", None))
        self.assertEqual(bsr.parse_kp("K.P 2+XXX"), ("KP2+XXX", None))

    def test_a_name_is_not_a_kp(self):
        self.assertEqual(bsr.parse_kp("TSA-BF06"), (None, None))


class LabelTest(unittest.TestCase):

    def test_a_disconnector_label_is_a_name_and_a_kp(self):
        label = bsr.parse_label("TSA-BF06\nKP93+451", "0_Disconnectors")
        self.assertEqual(label["kind"], "device")
        self.assertEqual(label["names"], ["TSA-BF06"])
        self.assertEqual(label["kp_m"], 93451)

    def test_a_slash_names_two_devices_of_the_same_installation(self):
        self.assertEqual(
            bsr.parse_label("UN2-NS3/NS4\nKP86+554", "0_Disconnectors")["names"],
            ["UN2-NS3", "UN2-NS4"],
        )
        self.assertEqual(bsr.expand_names("BSD-09/BSD-TE09"), ["BSD-09", "BSD-TE09"])

    def test_a_comma_list_replaces_the_tail_of_the_first_name(self):
        self.assertEqual(
            bsr.expand_names("DP2-TE105B, 104B, 103B"), ["DP2-TE105B", "DP2-TE104B", "DP2-TE103B"]
        )

    def test_a_space_after_the_dash_is_a_typo(self):
        self.assertEqual(
            bsr.parse_label("NRY- 03\nKP29+XXX", "0_Disconnectors")["names"], ["NRY-03"]
        )

    def test_name_and_kp_on_the_same_line(self):
        label = bsr.parse_label("HSA-FP1.1 KP9+316", "0_Disconnectors")
        self.assertEqual((label["names"], label["kp_m"]), (["HSA-FP1.1"], 9316))

    def test_neutral_sections_and_substations_are_context_not_devices(self):
        self.assertEqual(
            bsr.parse_label("NS ANAVA\nKP25+131", "0_Disconnectors")["kind"], "context"
        )
        self.assertEqual(bsr.parse_label("TS DOR\nKP29+700", "0_Disconnectors")["kind"], "context")
        self.assertEqual(bsr.parse_label("BY-PASS FEEDER FOR EMF", "0_Disconnectors")["names"], [])

    def test_feeders_inside_a_substation_are_not_matched_to_disconnectors(self):
        # 'F1.1' apunta a geometria gris dentro del recuadro de la subestacion: si se tratase como
        # un seccionador, la asignacion le daria el simbolo de catenaria de al lado.
        self.assertEqual(bsr.parse_label("F1.1", "0_Disconnectors")["kind"], "feeder")

    def test_depot_names_only_count_on_the_disconnector_layer(self):
        # Los del deposito de Haifa sin guion ('LB121'); con guion ('LE2211-9') se leen como
        # cualquier seccionador, y su posicion en la zona rayada ya los deja fuera.
        self.assertEqual(bsr.parse_label("LB121", "0_Disconnectors")["kind"], "depot")
        # 'L4' en la capa del deposito de Lod es una via, no un seccionador.
        self.assertEqual(bsr.parse_label("L4", "Lod_Depot")["names"], [])

    def test_station_code_from_the_name(self):
        self.assertEqual(bsr.station_code("TSA-BF06"), "TSA")
        self.assertEqual(bsr.station_code("ASKD-02BIS"), "ASK-D")
        self.assertEqual(bsr.station_code("DLOD-31"), "LOD_D")
        self.assertEqual(bsr.station_code("*TN1-TE1"), "TN1")
        self.assertEqual(bsr.station_code("F1.1"), "")


class FunctionTest(unittest.TestCase):

    def test_what_the_name_says(self):
        self.assertEqual(bsr.function_from_name("KAF-NS2"), "NS")
        self.assertEqual(bsr.function_from_name("TSA-BF06"), "BF")
        self.assertEqual(bsr.function_from_name("NIZ-B01"), "B")
        self.assertEqual(bsr.function_from_name("HFT-FP1.2"), "FP")
        self.assertEqual(bsr.function_from_name("TN3-TE1"), "TE")
        self.assertEqual(bsr.function_from_name("NIZ-04"), "")

    def test_proposal_when_there_is_no_pole_to_copy_from(self):
        self.assertEqual(bsr.function_proposal("SECCIONADOR", True, "", "IO"), "LoadB/IO")
        self.assertEqual(bsr.function_proposal("SECCIONADOR", False, "", "IO"), "Disc/IO")
        self.assertEqual(bsr.function_proposal("SECCIONADOR", True, "NS", "IO"), "LoadB/NS")
        self.assertEqual(bsr.function_proposal("SECCIONADOR", False, "", "SI"), "Disc/SI")
        self.assertEqual(
            bsr.function_proposal("SECCIONADOR DE PUESTA A TIERRA", False, "", ""), "ED"
        )
        # Sin pista no se inventa nada.
        self.assertEqual(bsr.function_proposal("SECCIONADOR", True, "", ""), "")


class GeometryTest(unittest.TestCase):

    # Un rotulo vertical (girado 90 grados) de 2 x 30 unidades, insertado en (100, 0).
    VERTICAL = (90.0, 100.0, 0.0, 0.0, 30.0, -2.0, 0.0)

    def test_a_symbol_below_a_vertical_label_is_in_its_column(self):
        du, dv = bsr.outside(self.VERTICAL, 101.0, -12.0)
        self.assertAlmostEqual(dv, 0.0)
        self.assertAlmostEqual(du, 12.0)

    def test_a_symbol_beside_the_label_is_off_the_column(self):
        du, dv = bsr.outside(self.VERTICAL, 106.0, 10.0)
        self.assertAlmostEqual(du, 0.0)
        self.assertAlmostEqual(dv, 4.0)

    def test_directions_not_senses(self):
        self.assertAlmostEqual(bsr.angle_diff(0, 180), 0.0)
        self.assertAlmostEqual(bsr.angle_diff(10, 170), 20.0)

    def test_kp_between_two_neighbours(self):
        self.assertAlmostEqual(bsr.interpolate_kp(5.0, [(0.0, 1000.0), (10.0, 1100.0)]), 1050.0)
        self.assertEqual(bsr.interpolate_kp(5.0, [(0.0, 1000.0)]), 1000.0)
        self.assertIsNone(bsr.interpolate_kp(5.0, []))


class MasterTrackTest(unittest.TestCase):

    TRACKS = [
        {"name": "TRACK 1", "num": "1", "stations": ["ZIC", "BIN", "HAD", "KFA"]},
        {"name": "TRACK 3 BIN", "num": "3", "stations": ["BIN"]},
        {"name": "TRACK 3 HAD", "num": "3", "stations": ["HAD"]},
        {"name": "TRACK 3 KFA", "num": "3", "stations": ["KFA"]},
    ]

    def test_the_number_of_the_master_track_name(self):
        self.assertEqual(bsr.track_number_of("TRACK 3 BIN"), "3")
        self.assertEqual(bsr.track_number_of("TRACK 04 KFA"), "4")
        self.assertIsNone(bsr.track_number_of("TRACK INT NORTH"))
        self.assertIsNone(bsr.track_number_of("TRACK 1.2 Ashdod - Nizanim - Ashkelon"))

    def test_a_unique_number_needs_no_station(self):
        self.assertEqual(bsr.master_track(self.TRACKS, "", "1"), ("TRACK 1", ""))

    def test_the_station_breaks_the_tie(self):
        self.assertEqual(bsr.master_track(self.TRACKS, "HAD", "3"), ("TRACK 3 HAD", ""))

    def test_no_station_no_choice(self):
        name, why = bsr.master_track(self.TRACKS, "", "3")
        self.assertEqual(name, "")
        self.assertIn("ambigua", why)

    def test_a_number_the_master_does_not_have(self):
        self.assertEqual(
            bsr.master_track(self.TRACKS, "BIN", "45"), ("", "la via 45 no existe en el maestro")
        )


class ConfidenceTest(unittest.TestCase):

    def test_a_leader_line_is_certain(self):
        self.assertEqual(bsr.confidence({"how": "leader"}), "A")

    def test_aligned_close_and_without_rival(self):
        self.assertEqual(
            bsr.confidence({"how": "aligned", "cost": 10, "du": 10, "dv": 0, "alt": 40}), "A"
        )

    def test_a_rival_almost_as_close_lowers_it(self):
        self.assertEqual(
            bsr.confidence({"how": "aligned", "cost": 10, "du": 10, "dv": 0, "alt": 12}), "B"
        )

    def test_the_relaxed_pass_is_always_doubtful(self):
        self.assertEqual(bsr.confidence({"how": "relaxed"}), "C")


if __name__ == "__main__":
    unittest.main()
