"""El port de la macro ab(), que da el angulo del brazo, y su lectura de los argumentos.

Los casos llevan los numeros de celdas de verdad: lo que leen y lo que guardo Excel con las
macros activas. `arm_angle.py check data/workbook` compara el port con las 9.453 que hay.

    python3 -m unittest discover -s data/tools/tests
"""

import importlib.util
import math
import os
import unittest

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(BASE, name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


aa = _load("arm_angle")


def sheet(**cells):
    """value_of(columna, fila) sobre unas celdas sueltas: 'T14' -> valor."""
    return lambda column, row: cells.get(f"{column}{row}")


class LaMacro(unittest.TestCase):

    def test_da_lo_mismo_que_excel(self):
        # EP7 / HR Track INT SOUTH (4(6)-1), AT20: =+ab(-$T$20,$T$18,$T$22,$G$19,$G$21, 1*$F$20,1200)
        celdas = sheet(T20=-35, T18=0, T22=-35, G19=28.5, G21=44.8, F20=0)
        self.assertEqual(aa.evaluate("=+ab(-$T$20,$T$18,$T$22,$G$19,$G$21, 1*$F$20,1200)", celdas),
                         6.843725017604912)
        # EP7, AT14, y EP11A / HR Track 1, AT136, que Excel guardo calculadas.
        self.assertEqual(aa.ab(-15, 30, 35, 37.1, 45, 0, 1200), -8.200383303675533)
        self.assertAlmostEqual(aa.ab(20, 20, 20, 50.4, 46.3, 0, 1200), 89.99896239693999, places=12)

    def test_un_radio_cero_es_recta(self):
        self.assertEqual(aa.ab(10, 0, 0, 40, 40, 0, 1200), aa.ab(10, 0, 0, 40, 40, 1e9, 1200))

    def test_usa_el_pi_de_la_macro(self):
        # 3.1416, no math.pi: Excel guardo -8.200383303675533 en EP7 AT14, y con el pi de
        # verdad saldria otro numero.
        con_pi_de_verdad = aa.ab(-15, 30, 35, 37.1, 45, 0, 1200) * 3.1416 / math.pi
        self.assertNotAlmostEqual(con_pi_de_verdad, -8.200383303675533, places=6)


class LoQueExcelGuardaComoValue(unittest.TestCase):

    def test_un_vano_vacio_divide_por_cero(self):
        # EP11B / HR Track 2, AT415: S416 vacia.
        with self.assertRaises(aa.VbaError):
            aa.evaluate("=+ab($T$415,$T$413,$T$417,$S$414,$S$416, 1*$R$415,1200)",
                        sheet(T415=20, T413=-20, T417=20, S414=45))

    def test_un_texto_no_es_un_numero_aunque_python_lo_lea(self):
        # EP9A / HR Track 1, R95 = 'inf': float('inf') valdria, VBA da Type mismatch.
        with self.assertRaises(aa.VbaError):
            aa.evaluate("=+ab($T$95,$T$93,$T$99,$G$94,$G$98,-1*$R$95,1200)",
                        sheet(T95=20, T93=-20, T99=20, G94=50, G98=50, R95="inf"))

    def test_un_argumento_ref(self):
        with self.assertRaises(aa.VbaError):
            aa.evaluate("=+ab($T$9,#REF!,$T$11,$G$8,$G$10, -1*$F$9,1200)",
                        sheet(T9=20, T11=20, G8=50, G10=50))


class LosArgumentos(unittest.TestCase):

    def test_signo_factor_y_parentesis(self):
        celdas = sheet(T269=20, T267=-20, U271=20, G268=40, G270=40, F269=500)
        self.assertEqual(
            aa.evaluate("=+ab($T$269,-$T$267,$U$271,$G$268,($G$270), 1*$F$269,1200)", celdas),
            aa.ab(20, 20, 20, 40, 40, 500, 1200))

    def test_un_numero_escrito_en_la_formula(self):
        # EP9A / HR Track 1 to Kafar, AT105, lleva un 60 en vez de una referencia.
        self.assertEqual(aa.evaluate("=+ab($T$105,$T$101,$T$103,$G$104,60, 1*$F$105,1200)",
                                     sheet(T105=10, T101=-10, T103=10, G104=50, F105=0)),
                         aa.ab(10, -10, 10, 50, 60, 0, 1200))

    def test_una_celda_vacia_vale_cero(self):
        self.assertEqual(aa.vba_number(None), 0.0)
        self.assertEqual(aa.vba_number(" 12.5 "), 12.5)

    def test_solo_lee_llamadas_a_ab(self):
        self.assertTrue(aa.is_ab("=+ab($T$9,$T$7,$T$11,$G$8,$G$10,-1*$F$9,1200)"))
        self.assertTrue(aa.is_ab("+ab(1,2,3,4,5,6,7)"))
        self.assertFalse(aa.is_ab("=+FL_1($T$14,$T$12,$G$13)"))
        self.assertFalse(aa.is_ab("='HR Track 1 RAAN-HER'!AT87"))


if __name__ == "__main__":
    unittest.main()
