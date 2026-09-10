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

# Rango de la longitud del brazo, tomado de la columna steady_arm.length.
STEADY_ARM_LENGTH_MIN, STEADY_ARM_LENGTH_MAX = 1, 2000

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


def code_tokens(text: str, noise: dict | None = None) -> list[str]:
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
    - ``'AnMP(T1)'`` -> ``AnMP``; ``'MP (Track 02)'`` -> ``AnMP``. El parentesis dice en
      que via esta, que ya lo sabe la via.

    ``noise`` son los trozos que esa columna escribe al lado del codigo sin que formen
    parte de el, declarados por entidad en ``aliases.yml``. En ANCHORAGE son la longitud
    de semitension que la leyenda llama ``xxx m.`` (``'CP+AnMC 265,00'``), la palabra
    ``Portal`` —que tiene su propia columna— y las anotaciones de via. Va por parametro
    y no fijo aqui porque lo que sobra depende de la columna: un numero suelto no es un
    codigo en ANCHORAGE, pero en otra columna podria serlo.

    Lo que NO hace es decidir si el resultado son codigos: eso lo comprueba cada
    generador contra su catalogo, y lo que no resuelve sale nombrado.
    """
    if not text:
        return []

    text = normalize_code(str(text))
    # (T1), (Track 02): la via, que ya la sabe la via. El (CS) de las agujas y el (Tg)
    # se quedan, porque ahi el parentesis SI es parte del codigo: exige un numero dentro.
    text = re.sub(r"\((?:T|Track)\s*\d+\)", "", text, flags=re.IGNORECASE)
    text = re.sub(r"(?<=\))(?=[^\s)])", " ", text)  # P50(CS)S/A -> P50(CS) S/A

    tokens: list[str] = []
    for token in text.split():
        if token == "-":                            # separador, no codigo
            continue
        if is_noise_token(token, noise):            # 'CP+AnMC 265,00' -> CP+AnMC
            continue
        previous = tokens[-1] if tokens else ""
        if re.fullmatch(r"Diag\d?", token, re.IGNORECASE) and previous.upper() == "A/S":
            tokens[-1] = "A/S-Diag"                 # 'A/S Diag' es 'A/S-Diag'
            continue
        if re.fullmatch(r"\d", token) and previous.upper() == "A/S-DIAG":
            continue                                # 'A/S-Diag 2': el 2 no es codigo
        tokens.append(token)
    return tokens


def is_noise_token(token: str, noise: dict | None) -> bool:
    """True si el trozo es algo escrito al lado del codigo y no parte de el.

    ``noise`` llega de la seccion ``code_noise_tokens`` de aliases.yml, con la misma
    forma que ``code_atoms``: una lista ``exact`` y un ``regex``. Devuelve False cuando
    no hay declaracion, que es el caso de casi todas las columnas.
    """
    if not noise or not token:
        return False
    upper = squash(token).upper()
    if any(squash(value).upper() == upper for value in noise.get("exact", [])):
        return True
    pattern = noise.get("regex")
    return bool(pattern) and re.fullmatch(pattern, squash(token), re.IGNORECASE) is not None


def split_steady_arm(raw, arm_types):
    """Parte 'PH-1150' en tipo y longitud. Devuelve (tipo, longitud, motivo).

    El catalogo SteadyArmType solo tiene el tipo base, pero el origen escribe tipo y
    longitud juntos. La regla es "sufijo numerico = longitud", y necesita la lista de
    tipos porque 'PH-C' y 'PH-Q' llevan guion sin ser una longitud.

    5.691 de las 9.776 celdas del maestro traen SOLO el tipo. No es un error: la
    longitud no se conoce, y por eso steady_arm.length es opcional.

    La usan LOS DOS generadores, por lo mismo que code_tokens: mientras solo la aplicaba
    el maestro de perfiles, el catalogo se quedaba con 60 filas —'PHQ-1150', 'PH-950'...—
    que no son tipos de brazo sino un tipo CON su longitud dentro del nombre. Nadie las
    referenciaba, pero estaban marcadas "pendiente de decidir", y habilitar una habria
    guardado la misma medida dos veces: en steady_arm.length y dentro del codigo.
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
