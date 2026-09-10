#!/usr/bin/env python3
"""Primitivas compartidas por los generadores de data/tools.

Las usan build_lov_master.py y build_profile_master.py, que leen LAS MISMAS hojas de
los mismos 11 workbooks. Viven aqui y no duplicadas en cada script porque el dia que
los dos discrepen sobre que es una celda vacia o donde esta la cabecera, uno de los
dos maestros saldra mal y nadie lo notara: los dos seguirian terminando con codigo 0.
"""

from __future__ import annotations

import glob
import os
import re

# Tope de filas por hoja. Protege de las hojas con formato aplicado a toda la
# cuadricula (EP9A declara 1.048.576 filas). La hoja Track mas larga que hemos
# medido tiene ~5.200 filas reales.
MAX_ROWS_TRACK = 20_000
MAX_COLS = 140

# Las hojas de trazado escriben sus datos en las columnas 1..52. De la 54 en
# adelante llevan la LEYENDA incrustada y las tablas resumen de cantones, tuneles y
# viaductos, que no son datos del perfil.
TRACK_DATA_MAX_COL = 52

# La cabecera esta en la fila 2 o en la 3 segun el fichero, asi que se localiza en
# lugar de darla por fija.
TRACK_HEADER_SEARCH_ROWS = 3
TRACK_HEADER_MIN_CELLS = 8

MAX_CODE_LEN = 40

# Valores que aparecen en las celdas de las hojas Track y que no son datos: marcas de
# columna vacia, subcabeceras de la fila siguiente a la cabecera y errores de formula.
TRACK_NOISE = {
    "0", "-", "TRUE", "FALSE", "P", "Ü",
    "M1", "M2", "M3", "D1", "D2", "D3", "H1", "H2", "H3",
    "E1", "E2", "E3", "B1", "B2", "B3", "W1", "W2", "W3", "A1", "A2", "A3",
}


def squash(value) -> str:
    """Colapsa espacios y saltos de linea. Devuelve '' para None."""
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def norm_header(value) -> str:
    return squash(value).lower()


def norm_category(value) -> str:
    return squash(value).upper()


def is_noise(code: str) -> bool:
    """True si la celda no puede ser un codigo.

    El '0' es el caso que mas importa: el origen lo usa como marcador de hueco en
    TODAS las columnas, incluida la del identificador del perfil.
    """
    if not code:
        return True
    if code.upper() in TRACK_NOISE:
        return True
    if code.startswith("#"):                       # #REF!, #NAME?, #N/A
        return True
    if re.fullmatch(r"[-+]?\d+([.,]\d+)?", code):  # numeros sueltos
        return True
    if re.search(r"\d{2}:\d{2}:\d{2}", code):      # fechas serializadas
        return True
    if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{2,4}", code):  # fechas tecleadas a mano
        return True
    return False


def is_blank(value) -> bool:
    """True si la celda no lleva dato, contando el '0' y el '-' del origen."""
    text = squash(value)
    return text == "" or text in {"0", "-"}


def is_track_sheet(name: str) -> bool:
    """True para las hojas de trazado.

    EP14A trae una hoja 'HTrack 46', sin la R: comparar sin espacios y admitiendo las
    dos formas evita perder sus 8 perfiles.
    """
    compact = squash(name).upper().replace(" ", "")
    return compact.startswith("HRTRACK") or compact.startswith("HTRACK")


def find_header_row(rows) -> int | None:
    """Indice (0-based) de la fila de cabecera dentro de las primeras filas.

    Es la primera con al menos TRACK_HEADER_MIN_CELLS celdas con contenido: la fila 1
    suele llevar solo el titulo de la via y la cabecera cae en la 2 o en la 3.
    """
    for index, row in enumerate(rows[:TRACK_HEADER_SEARCH_ROWS]):
        if sum(1 for value in row if value is not None) >= TRACK_HEADER_MIN_CELLS:
            return index
    return None


def discover(folder):
    """Todos los workbooks de la carpeta, sin depender de mayusculas.

    EP14A.XLSM y EP14B.XLSM traen la extension en mayusculas.
    """
    found = set()
    for pattern in ("*.xlsm", "*.xlsx", "*.XLSM", "*.XLSX"):
        found.update(glob.glob(os.path.join(folder, pattern)))
    return sorted(found)


def normalize_code(text: str) -> str:
    """Quita el espacio que sobra antes de un parentesis: 'P50 (CS)' es 'P50(CS)'.

    Es una errata de tecleo repetida en 50 codigos de cuatro catalogos, y no era inocua:
    'P50 (CS) S/A' no se reconocia como la concatenacion de 'P50(CS)' y 'S/A', asi que
    entraba al catalogo como si fuera un codigo mas.
    """
    return re.sub(r"\s+\(", "(", text)


def code_tokens(text: str) -> list[str]:
    """Parte una celda en los codigos que lleva dentro.

    Una celda de seccionamiento o de anclaje puede llevar VARIOS codigos, y el origen
    los separa por espacios. Partir por espacios a secas no vale: hay codigos que
    llevan espacio dentro y hay grafias que el espacio rompe por la mitad. Esta funcion
    aplica las reglas que la leyenda de los workbooks deja claras, y solo esas:

    - ``'P50 (CS) S/A'`` -> ``P50(CS)``, ``S/A``. El espacio antes del parentesis es una
      errata de tecleo repetida en 50 codigos de cuatro catalogos.
    - ``'P50(CS)S/A'`` -> ``P50(CS)``, ``S/A``. Dos codigos pegados sin espacio.
    - ``'A/S - S/A'`` -> ``A/S``, ``S/A``. El guion suelto separa, no es un codigo:
      'Overlap Anchorage' y 'Overlap Semi-Axis' son dos cosas distintas.
    - ``'A/S Diag'`` -> ``A/S-Diag``. 'Diagonal Anchorage' es ``A/S-Diag`` y **no existe
      un 'Diag' suelto**: cuando aparece detras de ``A/S`` es esa misma grafia escrita
      con espacio. Vale igual para ``'A/S Diag1'`` y ``'A/S Diag 2'``, donde el numero
      es la cuenta de diagonales del poste y no forma parte de ningun codigo.
    - ``'AnMP(T1)'`` -> ``AnMP``; ``'MP(T1)'`` -> ``MP``. El ``(T<n>)`` dice en que via
      esta, que ya lo sabe la via.

    Lo que NO hace es decidir si el resultado son codigos: eso lo comprueba cada
    generador contra su catalogo, y lo que no resuelve sale nombrado.
    """
    if not text:
        return []

    text = normalize_code(str(text))
    text = re.sub(r"\(T\d+\)", "", text)            # AnMP(T1) -> AnMP
    text = re.sub(r"(?<=\))(?=[^\s)])", " ", text)  # P50(CS)S/A -> P50(CS) S/A

    tokens: list[str] = []
    for token in text.split():
        if token == "-":                            # separador, no codigo
            continue
        previous = tokens[-1] if tokens else ""
        if re.fullmatch(r"Diag\d?", token, re.IGNORECASE) and previous.upper() == "A/S":
            tokens[-1] = "A/S-Diag"                 # 'A/S Diag' es 'A/S-Diag'
            continue
        if re.fullmatch(r"\d", token) and previous.upper() == "A/S-DIAG":
            continue                                # 'A/S-Diag 2': el 2 no es codigo
        tokens.append(token)
    return tokens
