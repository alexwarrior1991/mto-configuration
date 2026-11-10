#!/usr/bin/env python3
"""El angulo del brazo (columna 'Arm Angle') sin Excel: la macro ab() de los workbooks.

Las hojas HR Track calculan el angulo con una funcion de la macro de cada libro,

    Function ab(d, d1, d2, vano1, vano2, R, T)
        If (R = 0) Then R = 1000000000
        Pi = 3.1416 : dp1 = 3
        fb = T * ((d - d1) / 100 / vano1 + (d - d2) / 100 / vano2 + (vano1 + vano2) / 2 / R)
        ab = (Atn((0.818 + 1.067 * dp1) / fb)) * 180 / Pi
    End Function

—la misma en los once .xlsm—, y la celda guarda el ultimo resultado. Si el libro se recalcula
con las macros desactivadas, la funcion no existe y lo guardado es '#NAME?': asi llegaron las
dos hojas SOUTH de EP7. El generador lee lo guardado, no recalcula, asi que ese angulo se perdia.

    python3 data/tools/arm_angle.py check data/workbook          # el port contra lo que guardo Excel
    python3 data/tools/arm_angle.py fill data/workbook/EP7.xlsm  # escribe lo que falta

`check` recorre cada ab() con un valor guardado y lo compara con el port: tiene que coincidir en
todas (la diferencia es ruido de coma flotante, 1e-14), y donde Excel guardo '#VALUE!' el port
tiene que fallar tambien. `fill` solo toca las celdas ab() que guardan '#NAME?': escribe el numero
en su <v> y deja la formula, la macro y el resto del fichero como estaban. Una que la macro no
puede calcular —un argumento #REF!, un vano vacio, un texto— se queda como '#VALUE!', que es lo
que daria Excel, y el generador la sigue dejando sin angulo.
"""
from __future__ import annotations

import argparse
import collections
import glob
import math
import os
import re
import shutil
import sys
import zipfile
from xml.sax.saxutils import unescape

import openpyxl
from openpyxl.utils import column_index_from_string


class VbaError(Exception):
    """Lo que Excel guarda como #VALUE!: division por cero, un texto o un error en un argumento."""


def vba_number(value):
    """Lo que la macro recibe de una celda, ya como numero."""
    # Una celda vacia llega como Empty, que en una operacion vale 0.
    if value is None:
        return 0.0
    if isinstance(value, bool):
        return -1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if text.startswith("#"):
        raise VbaError(f"error en un argumento: {text}")
    # Solo un numero escrito como tal: Python leeria 'inf' o 'nan'; VBA no (Type mismatch).
    if not re.fullmatch(r"[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?", text):
        raise VbaError(f"texto en un argumento: {text!r}")
    return float(text)


def ab(d, d1, d2, vano1, vano2, R, T):
    """La funcion de la macro, tal cual: Pi = 3.1416 y dp1 = 3, no los de verdad."""
    if R == 0:
        R = 1000000000.0
    pi, dp1 = 3.1416, 3.0
    try:
        fb = T * ((d - d1) / 100 / vano1 + (d - d2) / 100 / vano2 + (vano1 + vano2) / 2 / R)
        return math.atan((0.818 + 1.067 * dp1) / fb) * 180 / pi
    except ZeroDivisionError:
        raise VbaError("division por cero") from None


TOKEN = re.compile(r"\s*(?:(\$?[A-Z]{1,3}\$?\d+)|(\d+(?:\.\d*)?|\.\d+)|(#REF!)|(.))")


def _tokens(text):
    out = []
    for ref, number, error, op in TOKEN.findall(text):
        if ref:
            out.append(("ref", ref.replace("$", "")))
        elif number:
            out.append(("num", float(number)))
        elif error:
            out.append(("err", error))
        elif op.strip():
            out.append(("op", op))
    return out


def _expression(tokens, value_of):
    """Los argumentos solo usan referencias, numeros, + - * / y parentesis: '-1*$F$9', '($G$270)'."""
    pos = 0

    def peek():
        return tokens[pos] if pos < len(tokens) else ("end", None)

    def take():
        nonlocal pos
        token = tokens[pos]
        pos += 1
        return token

    def atom():
        kind, value = take()
        if kind == "op" and value in "+-":
            number = atom()
            return -number if value == "-" else number
        if kind == "op" and value == "(":
            number = add()
            if take() != ("op", ")"):
                raise ValueError("parentesis sin cerrar")
            return number
        if kind == "num":
            return value
        if kind == "ref":
            column, row = re.fullmatch(r"([A-Z]+)(\d+)", value).groups()
            return vba_number(value_of(column, int(row)))
        if kind == "err":
            raise VbaError("#REF! en un argumento")
        raise ValueError(f"no se sabe leer {value!r}")

    def multiply():
        number = atom()
        while peek() in (("op", "*"), ("op", "/")):
            op = take()[1]
            other = atom()
            if op == "*":
                number *= other
            elif other == 0:
                raise VbaError("division por cero")
            else:
                number /= other
        return number

    def add():
        number = multiply()
        while peek() in (("op", "+"), ("op", "-")):
            op = take()[1]
            other = multiply()
            number = number + other if op == "+" else number - other
        return number

    number = add()
    if pos != len(tokens):
        raise ValueError(f"sobra {tokens[pos:]}")
    return number


def _arguments(text):
    depth, current, out = 0, "", []
    for char in text:
        if char == "," and depth == 0:
            out.append(current)
            current = ""
            continue
        depth += char == "("
        depth -= char == ")"
        current += char
    out.append(current)
    return out


def is_ab(formula):
    return isinstance(formula, str) and re.match(r"=?\+?ab\(", formula.strip()) is not None


def evaluate(formula, value_of):
    """El resultado de '=+ab(...)', con value_of(columna, fila) -> lo guardado en esa celda."""
    match = re.fullmatch(r"=?\+?ab\((.*)\)", formula.strip())
    if not match:
        raise ValueError(f"no es una llamada a ab(): {formula}")
    args = [_expression(_tokens(raw), value_of) for raw in _arguments(match.group(1))]
    if len(args) != 7:
        raise ValueError(f"ab() con {len(args)} argumentos: {formula}")
    return ab(*args)


def _reader(rows):
    def value_of(column, row):
        values = rows[row - 1] if row - 1 < len(rows) else ()
        index = column_index_from_string(column) - 1
        return values[index] if index < len(values) else None
    return value_of


def _sheet_rows(ws):
    return [list(row) for row in ws.iter_rows(values_only=True)]


def check(paths):
    """Compara el port con lo que guardo Excel. Devuelve (recuento, discrepancias)."""
    stats, mismatches = collections.Counter(), []
    for path in paths:
        values = openpyxl.load_workbook(path, read_only=True, data_only=True)
        formulas = openpyxl.load_workbook(path, read_only=True)
        for ws in formulas.worksheets:
            if not ws.title.upper().startswith("HR"):
                continue
            stored = _sheet_rows(values[ws.title])
            value_of = _reader(stored)
            for number, row in enumerate(ws.iter_rows(values_only=True), 1):
                for index, formula in enumerate(row):
                    if not is_ab(formula):
                        continue
                    cached = stored[number - 1][index] if index < len(stored[number - 1]) else None
                    where = (os.path.basename(path), ws.title, number, index + 1)
                    try:
                        result = evaluate(formula, value_of)
                    except VbaError:
                        stats["no calculable"] += 1
                        if isinstance(cached, (int, float)):
                            mismatches.append((*where, cached, "error en el port"))
                        continue
                    if isinstance(cached, (int, float)):
                        stats["comparadas"] += 1
                        if abs(result - cached) > 1e-9:
                            mismatches.append((*where, cached, result))
                    else:
                        stats[f"calculable, guardado {cached}"] += 1
                        if cached != "#NAME?":
                            mismatches.append((*where, cached, result))
    return stats, mismatches


def _sheet_parts(z):
    """{nombre de hoja: parte del zip}, de workbook.xml y sus relaciones."""
    workbook = z.read("xl/workbook.xml").decode("utf-8")
    rels = z.read("xl/_rels/workbook.xml.rels").decode("utf-8")
    targets = dict(re.findall(r'Id="([^"]+)"[^>]*?Target="([^"]+)"', rels))
    targets.update({rid: target for target, rid in
                    re.findall(r'Target="([^"]+)"[^>]*?Id="([^"]+)"', rels)})
    parts = {}
    for name, rid in re.findall(r'<sheet [^>]*?name="([^"]+)"[^>]*?r:id="([^"]+)"', workbook):
        target = targets[rid].lstrip("/")
        parts[unescape(name, {"&apos;": "'", "&quot;": '"'})] = (
            target if target.startswith("xl/") else "xl/" + target)
    return parts


NAME_ERROR = re.compile(
    r'<c r="([A-Z]+)(\d+)"([^>]*?) t="e"([^>]*)><f([^>]*)>([^<]*)</f><v>#NAME\?</v></c>')


def fill(path):
    """Escribe el resultado de ab() donde el libro guardo #NAME?. Devuelve [(hoja, celda, valor)]."""
    values = openpyxl.load_workbook(path, read_only=True, data_only=True)
    with zipfile.ZipFile(path) as z:
        parts = _sheet_parts(z)
        data = {name: z.read(name) for name in z.namelist()}
        infos = z.infolist()

    written, changed = [], {}
    for sheet, part in parts.items():
        xml = data[part].decode("utf-8")
        if "#NAME?" not in xml:
            continue
        value_of = _reader(_sheet_rows(values[sheet]))

        def replace(match, sheet=sheet, value_of=value_of):
            column, row, before, after, f_attrs, formula = match.groups()
            if not is_ab(unescape(formula)):
                return match.group(0)
            try:
                result = evaluate(unescape(formula), value_of)
            except VbaError:
                written.append((sheet, f"{column}{row}", "#VALUE!"))
                return match.group(0).replace("<v>#NAME?</v>", "<v>#VALUE!</v>")
            written.append((sheet, f"{column}{row}", result))
            return (f'<c r="{column}{row}"{before}{after}><f{f_attrs}>{formula}</f>'
                    f'<v>{result!r}</v></c>')

        new = NAME_ERROR.sub(replace, xml)
        if new != xml:
            changed[part] = new

    if changed:
        temporary = path + ".tmp"
        with zipfile.ZipFile(temporary, "w") as out:
            for item in infos:
                content = changed.get(item.filename)
                out.writestr(item, content.encode("utf-8") if content is not None
                             else data[item.filename])
        shutil.move(temporary, path)
    return written


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check").add_argument("folder", nargs="?", default="data/workbook")
    sub.add_parser("fill").add_argument("workbook")
    args = parser.parse_args(argv)

    if args.command == "check":
        paths = sorted(glob.glob(os.path.join(args.folder, "*.[xX][lL][sS][mM]")))
        stats, mismatches = check(paths)
        for key, count in sorted(stats.items()):
            print(f"  {key:30} {count}")
        for mismatch in mismatches:
            print("  DISCREPA", *mismatch)
        return 1 if mismatches else 0

    written = fill(args.workbook)
    counts = collections.Counter((sheet, "numero" if isinstance(value, float) else value)
                                 for sheet, _, value in written)
    for (sheet, kind), count in sorted(counts.items()):
        print(f"  {sheet}: {count} {kind}")
    if not written:
        print("  nada que escribir: ninguna ab() guarda #NAME?")
    return 0


if __name__ == "__main__":
    sys.exit(main())
