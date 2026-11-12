#!/usr/bin/env python3
"""Saca los seccionadores y los aisladores de seccion del plano DXF de seccionamiento.

Que hace
--------
Lee el plano de operacion ("Operational disconnectors 28.07.25 (based on OCS-SCADA_PD_R38)",
exportado a DXF) y escribe un libro de REVISION con las hojas DISCONNECTORS y SECTION_INSULATORS,
que llevan primero las columnas que build_profile_master.py copia a profile-master.xlsx, cruzadas
con los postes del propio maestro. Las agujas de cada aislador van en su fila, y de ellas saca
build_profile_master.py la hoja SECTION_INSULATOR_SWITCHES del maestro. Una persona completa y
valida ese libro; nada de lo que sale de aqui va directo a la aplicacion.

Por que no lo lee la aplicacion
-------------------------------
El plano es un esquema, no una base de datos. Los seccionadores son bloques dinamicos cuyo
tipo (on-load / off-load) y cuyo estado (cuchilla abierta o cerrada) solo estan en la
geometria visible de cada representacion anonima; el nombre y el KP son un MTEXT suelto
colocado encima o debajo, a veces unido por una linea de referencia y a veces solo alineado
en columna; y el KP de los aisladores ni siquiera esta rotulado. Todo eso es heuristico, se
equivoca de vez en cuando y necesita ojos humanos. Por eso vive aqui, como los otros dos
generadores, y su salida es un libro con cada duda señalada, no un maestro.

Regla de oro: nunca descartar en silencio
-----------------------------------------
Todo simbolo del plano sale en el inventario (SECCIONADORES_DXF, AISLADORES_DXF) con su
estado de dibujo, y todo rotulo sin simbolo sale en ETIQUETAS_SIN_SIMBOLO. Lo que no entra en
las hojas del maestro dice por que.

Uso
---
    pip install ezdxf scipy openpyxl
    python3 data/tools/build_sectioning_review.py plano.dxf \
        [-m data/profile-master.xlsx] [-o salida.xlsx]

    # Despues de editar el libro: RESUMEN y PENDIENTE, de sus hojas de datos. Sin el plano.
    python3 data/tools/build_sectioning_review.py --refresh data/sectioning-review.xlsx
    python3 data/tools/build_sectioning_review.py --check data/sectioning-review.xlsx

El DXF no esta en el repositorio (46 MB; ver "El plano no esta en el repositorio" en
data/README.md): sin el no se puede generar el libro, y el script lo dice y termina. El libro
versionado, data/sectioning-review.xlsx, no es la salida de este script sino esa salida ya
revisada a mano: generarlo otra vez no trae la revision, y por eso no se escribe encima.
"""

from __future__ import annotations

import argparse
import collections
import math
import os
import re
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.dirname(BASE)

# --------------------------------------------------------------------------------
# Lo que el plano llama cada cosa
# --------------------------------------------------------------------------------

# Bloques que dibujan un seccionador. DISCONNECTOR es el dinamico de la red (1.168 inserciones:
# visibilidad OFF-LOAD/ON-LOAD, giro de la cuchilla); Disc_open/Disc_closed son los del deposito
# de Haifa/Kishon; OnLoad_Circuit_breaker_opened, cuatro by-pass de zona neutra.
DISCONNECTOR_BLOCKS = {
    "DISCONNECTOR",
    "Disc_open",
    "Disc_closed",
    "EARTHING DISCONNECTOR 2",
    "OnLoad_Circuit_breaker_opened",
}
SECTION_INSULATOR_BLOCK = "Section_insulator"
INSULATED_OVERLAP_BLOCK = "Insu_Overlap"

# Capas donde viven los rotulos de los seccionadores y sus lineas de referencia.
LABEL_LAYERS = {
    "0_Disconnectors",
    "0_Earthing Disc",
    "0_NeutralSection_Disabled",
    "0_NeutralSections_FutureStage",
    "0_FutureSectionEquip",
    "Lod_Depot",
}
LEADER_LAYERS = LABEL_LAYERS | {"0", "0_Voltage Detector"}

# Capas que nunca son via aunque lleven polilineas gruesas: contornos de EP, recuadros de
# subestacion, textos.
NOT_TRACK_LAYERS = {
    "0_Ep's",
    "Sinalling info",
    "0_Disconnectors",
    "0_Turnouts Text",
    "0_FutureSectionEquip",
    "0_Text",
    "0_Stations",
}

# El recuadro rayado en verde abajo a la izquierda (deposito de Haifa/Kishon y Hutzot
# Hamifratz) es de EP futuros que el maestro no tiene. Lo que cae dentro no recibe EP nunca.
HAIFA_DEPOT_BOX = (-1000.0, -4300.0, 2400.0, -3600.0)

# Estados de dibujo. NO deciden lo que entra en las hojas del maestro: entra todo lo que cae en
# un EP del maestro, este dibujado en servicio, como futuro, en via no electrificada o en la zona
# neutra deshabilitada de Holtz, y su estado sale en ESTADO_DIBUJO. Lo unico que queda fuera es lo
# que no esta en esos EP, empezando por el deposito de Haifa.
EN_SERVICE = "EN SERVICIO"
STATUS_BY_LAYER = {
    "0_FutureSectionEquip": "FUTURO",
    "0_NeutralSections_FutureStage": "FUTURO",
    "0_NeutralSection_Disabled": "DESHABILITADO (NS)",
    "0_Non Electrified Track": "VIA NO ELECTRIFICADA",
}
HAIFA_STATUS = "DEPOSITO HAIFA/KISHON (EP futuro)"

SYMBOL_TYPE = {
    "DISCONNECTOR": "SECCIONADOR",
    "EARTHING DISCONNECTOR 2": "SECCIONADOR DE PUESTA A TIERRA",
    "Disc_open": "SECCIONADOR (bloque de deposito)",
    "Disc_closed": "SECCIONADOR (bloque de deposito)",
    "OnLoad_Circuit_breaker_opened": "INTERRUPTOR EN CARGA",
    "EXPLODED": "SECCIONADOR (dibujo explotado)",
}

# Tolerancias, en unidades del dibujo (el plano no esta a escala: 1 unidad no es 1 metro).
CLOSED_BLADE_MAX_ANGLE = 8.0  # cuchilla a menos de 8 grados del eje de contactos = cerrada
LEADER_HIT = 2.0  # la punta de una linea de referencia toca el simbolo
POLE_MATCH_MAX_M = 80.0  # poste con codigo de seccionador a menos de 80 m del KP
POLE_PROPOSAL_MAX_M = 30.0  # poste sin codigo propuesto solo si esta a menos de 30 m
POLE_OPTIONS_MAX = 8  # postes en el desplegable de PROFILE_ID, del mas cercano al mas lejano
BYPASS_NAME_FUNCTIONS = ("B", "BF")  # la B del by-pass en el nombre (HER-B02, THS-BF01)
PARALLEL_FUNCTION = "/PP"  # Disc/PP, LoadB/PP y sus -pr: puesta en paralelo de dos vias
DISCONNECTOR_CODE_PREFIXES = ("Disc", "LoadB", "ED")  # los codigos de catalogo de un seccionador
# ESTACION de un seccionador que no es de ninguna (en plena via, una zona neutra, una subestacion).
# Se escribe: en blanco seria un olvido, y el importador lo rechaza.
WITHOUT_STATION = "SIN ESTACION"
ORPHAN_LABEL_MAX_DIST = 300.0  # rotulo suelto que se ofrece como nombre de un simbolo sin rotulo
SECT_I_MATCH_MAX_M = 120.0
SWITCH_TIE = 3.0  # dos rotulos de aguja a menos de 3 unidades de diferencia: empate

# Prefijos del rotulo que no coinciden con el codigo de estacion del maestro.
STATION_ALIAS = {"ASKD": "ASK-D", "ASKS": "ASK-S", "DLOD": "LOD_D", "LOD": "LOD_S", "MSM": "MSMR"}
# Estaciones del maestro que el plano rotula sin el codigo entre parentesis, solo con el nombre y
# el numero debajo ('TZOMET REMEZ\n30'): el nombre, en mayusculas, y su codigo.
PLAN_STATION_NAMES = {"TZOMET REMEZ": "REM", "PLESHET": "PST"}

# --------------------------------------------------------------------------------
# Rotulos: nombre y KP
# --------------------------------------------------------------------------------

KP_RE = re.compile(r"\(?\s*K\.?\s*P\.?\s*([0-9X]{1,3})\s*\+\s*([0-9X]{3})(\.\d+)?\s*\)?", re.I)
BARE_KP_RE = re.compile(r"^\(?\s*(\d{1,3})\+(\d{3})(\.\d+)?\s*\)?$")
NAME_RE = re.compile(
    r"^\*?[A-Z][A-Z0-9]{0,5}-[A-Z0-9][A-Z0-9.\-]*(/[A-Z0-9.\-]+)*(,\s*[A-Z0-9.\-]+)*$"
)
NOT_DEVICE_RE = re.compile(r"^(BY-PASS|NON-|SEMI-)")
DEPOT_NAME_RE = re.compile(r"^(L[A-Z]{1,3}|DB)-?\d[\d\-]*$")
# 'F1.1', 'F2.2-1': interruptores de feeder DENTRO de una subestacion. No son seccionadores de
# catenaria y apuntan a geometria gris del recuadro de la subestacion, no a un simbolo.
FEEDER_RE = re.compile(r"^F\d(\.\d)*(-\d)?$")
CONTEXT_RE = re.compile(r"^(NS|TS)\s+\S")


def parse_kp(text):
    """'KP93+451' -> ('KP93+451', 93451.0). Una X en el KP ('KP47+XXX') es KP desconocido."""
    m = KP_RE.search(text) or BARE_KP_RE.search(text.strip())
    if not m:
        return None, None
    km, metres, decimals = m.group(1), m.group(2), m.group(3) or ""
    label = "KP%s+%s%s" % (km, metres, decimals)
    if "X" in (km + metres).upper():
        return label, None
    return label, int(km) * 1000 + int(metres) + (float(decimals) if decimals else 0.0)


def expand_names(name):
    """Un rotulo puede nombrar varios aparatos.

    'UN2-NS3/NS4' son dos (NS3 y NS4 de la misma zona neutra); 'BSD-09/BSD-TE09' tambien;
    'DP2-TE105B, 104B, 103B' son tres y cada uno sustituye la cola del primero.
    """
    if "," in name:
        parts = [p.strip() for p in name.split(",") if p.strip()]
        first = parts[0]
        return [first] + [first[: len(first) - len(p)] + p for p in parts[1:]]
    if "/" in name:
        parts = [p.strip() for p in name.split("/") if p.strip()]
        prefix = parts[0].split("-")[0] + "-"
        return [parts[0]] + [p if "-" in p else prefix + p for p in parts[1:]]
    return [name]


def parse_label(text, layer):
    """Nombre(s), KP y clase de un rotulo de la capa de seccionadores.

    kind: 'device' (seccionador), 'depot' (deposito de Haifa, 'LE2211-9'), 'feeder' (dentro de
    una subestacion), 'context' ('NS ANAVA', 'TS DOR'), 'kp_only' o 'other'.
    """
    names, kps, kind = [], [], None
    for line in [l.strip() for l in text.replace("\t", " ").split("\n") if l.strip()]:
        kp_text, kp_m = parse_kp(line)
        rest = KP_RE.sub("", line).strip() if kp_text else line
        rest = re.sub(r"^([A-Z0-9]+)-\s+(\d)", r"\1-\2", rest).strip()  # 'NRY- 03'
        if kp_text:
            kps.append((kp_text, kp_m))
        if not rest:
            continue
        if CONTEXT_RE.match(rest) or rest.startswith("("):
            kind = "context"
        elif NAME_RE.match(rest) and not NOT_DEVICE_RE.match(rest):
            names.extend(expand_names(rest))
        elif DEPOT_NAME_RE.match(rest.rstrip(" -")) and layer == "0_Disconnectors":
            names.append(rest.rstrip(" -"))
            kind = "depot"
        elif FEEDER_RE.match(rest):
            names.append(rest)
            kind = "feeder"
    if names:
        kind = kind if kind in ("depot", "feeder") else "device"
    else:
        kind = kind or ("kp_only" if kps else "other")
    return {
        "names": names,
        "kind": kind,
        "kp_txt": kps[0][0] if kps else None,
        "kp_m": kps[0][1] if kps else None,
    }


def station_code(name):
    """Prefijo del nombre como codigo de estacion: 'TSA-BF06' -> 'TSA', '*TN1-TE1' -> 'TN1'."""
    if "-" not in name:
        return ""
    prefix = name.lstrip("*").split("-")[0]
    return STATION_ALIAS.get(prefix, prefix)


def function_from_name(name):
    """Lo que dice el nombre: NS zona neutra, B/BF by-pass, FP alimentacion, TE tierra."""
    if not name:
        return ""
    suffix = name.split("-", 1)[1] if "-" in name else name
    for pattern, code in (
        (r"^NS\d", "NS"),
        (r"^BF\d", "BF"),
        (r"^B\d", "B"),
        (r"^FP", "FP"),
        (r"^TE\d", "TE"),
        (r"^T\d", "T"),
        (r"^PRV", "PRV"),
    ):
        if re.match(pattern, suffix):
            return code
    if "/" in name and "TE" in name:
        return "TE"
    return ""


def function_proposal(kind, on_load, name_function, bridged, parallel=False):
    """Codigo del catalogo DisconnectorFunction cuando no hay poste del que copiarlo.

    parallel: el seccionador pone dos vias en paralelo (la B del by-pass y las patas en dos vias
    del plano). Gana a lo que puentea: el aislador o la lamina de aire que hay entre sus patas es
    el que separa las dos vias, y el maestro marca esos postes con PP, no con SI ni con IO.
    """
    if kind == "SECCIONADOR DE PUESTA A TIERRA" or name_function == "TE":
        return "ED"
    if name_function == "NS":
        return "LoadB/NS" if on_load else "Disc/NS"
    if name_function == "FP":
        return "LoadB" if on_load else "Disc"
    if parallel:
        return "LoadB/PP" if on_load else "Disc/PP"
    if bridged == "IO":
        return "LoadB/IO" if on_load else "Disc/IO"
    if bridged == "SI" and not on_load:
        return "Disc/SI"
    return ""


def connected_track(r, via, tracks_of_ep, station, on_a_pole=False):
    """VIA_CONECTADA de uno que pone dos vias en paralelo: la del maestro para la OTRA del plano.

    Devuelve (nombre, motivo si no se pudo). Solo se propone cuando una de las vias del plano es la
    suya y queda una sola distinta: si ninguna es la suya (la del poste sin numero, INT o EXT, o un
    plano que numera las vias de otra manera), no hay como saber cual de las dos es la otra.

    Con poste, que ninguna sea la suya puede querer decir tambien que cuelga del poste de una via y
    une dos que no son la del poste (un haz de vias: talleres, cocheras). El maestro lo admite: VIA
    es entonces la que maniobra y VIA_CONECTADA la otra. No se propone solo, porque el plano numera
    las vias de la estacion y no las del maestro: el motivo dice cuales serian, y lo decide quien
    revisa.
    """
    resolved, problems = [], []
    for number in dict.fromkeys(r["tracks"]):
        name, why = master_track(tracks_of_ep, station, number)
        if name:
            resolved.append(name)
        elif why:
            problems.append(why)
    plan = "el plano une las vias %s" % "/".join(r["tracks"])
    if via not in resolved:
        why = "%s y ninguna es la suya (%s)" % (plan, via or "sin via")
        if on_a_pole:
            pair = list(dict.fromkeys(resolved))
            why += (
                "; si cuelga de ese poste y maniobra otras, pon en VIA la que maniobra y en "
                "VIA_CONECTADA la otra%s, y deja el poste"
                % ((" (segun el plano, %s y %s)" % tuple(pair)) if len(pair) == 2 else "")
            )
        return "", why
    others = [name for name in dict.fromkeys(resolved) if name != via]
    if len(others) == 1:
        return others[0], ""
    if others:
        return "", "%s: %s" % (plan, " o ".join(others))
    return "", "%s; %s" % (plan, "; ".join(problems) or "la otra no es una via del maestro")


def earthing(r):
    """De puesta a tierra: el bloque del plano, o TE en el nombre ('TN3-TE3')."""
    return r["type"] == "SECCIONADOR DE PUESTA A TIERRA" or r["name_function"] == "TE"


def on_portal(r):
    """De alimentacion (FP en el nombre, 'HSA-FP1.1'): va en el portico de la subestacion."""
    return r["name_function"] == "FP"


def pole_cost(r, p):
    """Coste de casar el seccionador r con el poste p, en metros; None si no puede ser el suyo.

    La diferencia de KP, +60 si la via del plano no es la del poste y +25 si on-load no casa con
    LoadB/Disc. Uno de puesta a tierra solo va en un poste que el maestro marca con ED (ED, ED/T,
    LoadB/ED), y uno de linea no va en un poste que solo lleva ED o ED/T.
    """
    dk = abs(p["kp"] - r["kp_m"])
    if dk > POLE_MATCH_MAX_M:
        return None
    if earthing(r) and not any("ED" in x for x in p["dcodes"]):
        return None
    if not earthing(r) and all(x.startswith("ED") for x in p["dcodes"]):
        return None
    c = dk
    if r["tracks"] and p["track"] and p["track"] not in r["tracks"]:
        c += 60
    load_break = any(x.startswith("LoadB") for x in p["dcodes"])
    if not earthing(r) and load_break != r["on_load"]:
        c += 25
    return c


def future_ep(status):
    """El deposito de Haifa es de EP futuros que el maestro no tiene: nunca recibe EP."""
    return status == HAIFA_STATUS


def normally_open(state):
    """La cuchilla dibujada en la columna NORMALLY_OPEN: ABIERTO = SI, CERRADO = NO."""
    return {"ABIERTO": "SI", "CERRADO": "NO"}.get(state, "")


def drive_type(has_drive):
    """El circulo del accionamiento es el motor; sin el, el plano no dice como se acciona."""
    return "MOTOR" if has_drive else ""


# --------------------------------------------------------------------------------
# Geometria plana
# --------------------------------------------------------------------------------


def outside(obox, x, y):
    """Cuanto se sale el punto (x, y) de la caja orientada de un texto.

    obox = (giro, x0, y0, u0, u1, v0, v1): la caja del texto sin girar, relativa a su punto
    de insercion. Devuelve (du, dv): du a lo largo de la direccion de lectura y dv de
    traves. Un simbolo 'en columna' con su rotulo tiene dv = 0.
    """
    rot, ox, oy, u0, u1, v0, v1 = obox
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    dx, dy = x - ox, y - oy
    u = dx * c + dy * s
    v = -dx * s + dy * c
    return max(0.0, u0 - u, u - u1), max(0.0, v0 - v, v - v1)


def obox_center(obox):
    rot, ox, oy, u0, u1, v0, v1 = obox
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    cu, cv = (u0 + u1) / 2, (v0 + v1) / 2
    return ox + cu * c - cv * s, oy + cu * s + cv * c


def project(x, y, seg):
    """Distancia del punto al segmento, parametro t y pie de la perpendicular."""
    x0, y0, x1, y1 = seg[:4]
    dx, dy = x1 - x0, y1 - y0
    length2 = dx * dx + dy * dy
    if length2 == 0:
        return math.hypot(x - x0, y - y0), 0.0, x0, y0
    t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / length2))
    px, py = x0 + t * dx, y0 + t * dy
    return math.hypot(x - px, y - py), t, px, py


def seg_angle(seg):
    return math.degrees(math.atan2(seg[3] - seg[1], seg[2] - seg[0])) % 180


def angle_diff(a, b):
    """Diferencia entre dos direcciones (no sentidos), en [0, 90]."""
    return abs((a - b + 90) % 180 - 90)


def track_number_of(name):
    """'TRACK 3 BIN' -> '3', 'TRACK 04 KFA' -> '4'; 'TRACK INT NORTH' o 'TRACK 1.2 ...' -> None."""
    m = re.match(r"TRACK\s*0*(\d+)(?![\d.])", str(name))
    return m.group(1) if m else None


def master_track(tracks_of_ep, station, number):
    """Via del maestro para el numero de via del plano.

    tracks_of_ep: [{'name', 'num', 'stations'}]. Devuelve (nombre, motivo si no se pudo).
    Varias candidatas se desempatan por la estacion; si sigue habiendo mas de una, no se elige.
    """
    if not number:
        return "", "numero de via del plano sin localizar"
    candidates = [t for t in tracks_of_ep if t["num"] == str(number)]
    if len(candidates) == 1:
        return candidates[0]["name"], ""
    if station:
        same = [t for t in candidates if station in t["stations"]]
        if len(same) == 1:
            return same[0]["name"], ""
        only = [t for t in same if len(t["stations"]) == 1]
        if len(only) == 1:
            return only[0]["name"], ""
    if not candidates:
        return "", "la via %s no existe en el maestro" % number
    return "", "via %s ambigua: %s" % (number, " / ".join(t["name"] for t in candidates))


def interpolate_kp(x, neighbours):
    """KP a la abscisa x por interpolacion lineal entre el vecino de cada lado.

    neighbours: [(x, kp)]. El plano no esta a escala. Contra los 165 aisladores del libro revisado
    que tienen estimacion, el error mediano es de 41 m, uno de cada cinco se va de mas de 370 m y
    28 de mas de 1 km: los vecinos van con el KP de otra via o de otro tramo.
    """
    left = sorted([n for n in neighbours if n[0] <= x], key=lambda n: x - n[0])
    right = sorted([n for n in neighbours if n[0] > x], key=lambda n: n[0] - x)
    if left and right:
        (xa, ka), (xb, kb) = left[0], right[0]
        if xb == xa:
            return ka
        return ka + (x - xa) / (xb - xa) * (kb - ka)
    if left or right:
        return (left or right)[0][1]
    return None


# --------------------------------------------------------------------------------
# Lectura del DXF (ezdxf)
# --------------------------------------------------------------------------------


def _points(entity):
    t = entity.dxftype()
    if t == "LINE":
        return [(entity.dxf.start.x, entity.dxf.start.y), (entity.dxf.end.x, entity.dxf.end.y)]
    if t == "LWPOLYLINE":
        pts = [(float(x), float(y)) for x, y in entity.get_points("xy")]
        if entity.closed and pts:
            pts.append(pts[0])
        return pts
    return []


def _original_block(doc, name):
    """Nombre del bloque dinamico del que es representacion un bloque anonimo '*U123'."""
    if not name.startswith("*"):
        return name
    record = doc.blocks.get(name).block_record
    try:
        handles = [v for code, v in record.get_xdata("AcDbBlockRepBTag") if code == 1005]
        if handles:
            return doc.entitydb[handles[0]].dxf.name
    except Exception:  # sin xdata: no es representacion de nada
        pass
    return name


def _drawing_status(layer, x, y):
    if layer in STATUS_BY_LAYER:
        return STATUS_BY_LAYER[layer]
    x0, y0, x1, y1 = HAIFA_DEPOT_BOX
    if x0 <= x <= x1 and y0 <= y <= y1:
        return HAIFA_STATUS
    return EN_SERVICE


def read_dxf(path):
    """Simbolos, equipos, lineas y textos del espacio modelo, en coordenadas del dibujo."""
    import ezdxf
    from ezdxf import bbox
    from ezdxf.math import Vec3

    doc = ezdxf.readfile(path)
    msp = doc.modelspace()
    symbols = []

    def on_load_hatch(entities, centre, radius):
        # ON-LOAD: una cuerda y el sector relleno dentro del circulo de accionamiento.
        for h in entities:
            if h.dxftype() != "HATCH":
                continue
            try:
                box = bbox.extents([h])
            except Exception:
                continue
            if (
                box.has_data
                and abs(box.center.x - centre[0]) < radius
                and abs(box.center.y - centre[1]) < radius * 1.05
                and max(box.size.x, box.size.y) > radius
            ):
                return True
        return False

    for insert in msp.query("INSERT"):
        block = _original_block(doc, insert.dxf.name)
        if block not in DISCONNECTOR_BLOCKS:
            continue
        m = insert.matrix44()
        scale = abs(insert.dxf.xscale)
        visible = [e for e in doc.blocks.get(insert.dxf.name) if not e.dxf.get("invisible", 0)]
        segs, circles, blade, drive, vd, ct, square = [], [], None, None, False, False, False
        for e in visible:
            t = e.dxftype()
            if e.dxf.layer == "0_Voltage Detector" or (
                t == "INSERT" and e.dxf.name in ("SPW", "VT")
            ):
                vd = True
            if t in ("LWPOLYLINE", "LINE"):
                pts = _points(e)
                world = [m.transform(Vec3(a, b, 0)) for a, b in pts]
                segs.append([(round(v.x, 3), round(v.y, 3)) for v in world])
                if t == "LWPOLYLINE" and e.dxf.get("const_width", 0) >= 0.3 and len(pts) == 2:
                    blade = [(round(v.x, 3), round(v.y, 3)) for v in world]
                if t == "LWPOLYLINE" and e.closed and len(pts) == 5:
                    square = True
            elif t == "CIRCLE":
                c = m.transform(e.dxf.center)
                circles.append((round(c.x, 3), round(c.y, 3), round(e.dxf.radius * scale, 3)))
                if e.dxf.radius > 0.9:
                    drive = (e.dxf.center, e.dxf.radius, c)
            elif t == "ARC" and abs(e.dxf.radius - 0.70) < 0.05:
                ct = True
        symbol = {
            "handle": insert.dxf.handle,
            "block": block,
            "layer": insert.dxf.layer,
            "x": insert.dxf.insert.x,
            "y": insert.dxf.insert.y,
            "segs": segs,
            "circles": circles,
            "blade": blade,
            "vd": vd,
            "ct": ct,
            "square": square,
            "cod": " ".join(a.dxf.text for a in insert.attribs).strip(" -"),
            "on_load": False,
        }
        if drive:
            symbol["drive"] = (drive[2].x, drive[2].y)
            local = (drive[0].x, drive[0].y)
            symbol["on_load"] = on_load_hatch(visible, local, drive[1])
        symbols.append(symbol)

    # Simbolos explotados: un circulo de accionamiento suelto con su cuchilla al lado. Son 33
    # (los de la zona neutra futura de Remez, los de via no electrificada y cuatro by-pass).
    loose = [
        e
        for e in msp
        if e.dxftype() in ("CIRCLE", "LWPOLYLINE", "LINE", "HATCH") and e.dxf.layer != "0_Signals"
    ]
    grid = collections.defaultdict(list)
    for e in loose:
        try:
            box = bbox.extents([e], fast=True)
        except Exception:
            continue
        if not box.has_data or box.size.x > 15 or box.size.y > 15:
            continue
        grid[(int(box.center.x // 10), int(box.center.y // 10))].append(
            (e, (box.center.x, box.center.y))
        )

    def near(x, y, r):
        found = []
        for gx in range(int((x - r) // 10), int((x + r) // 10) + 1):
            for gy in range(int((y - r) // 10), int((y + r) // 10) + 1):
                found.extend(
                    e for e, c in grid.get((gx, gy), []) if math.hypot(c[0] - x, c[1] - y) <= r
                )
        return found

    for d in [e for e in loose if e.dxftype() == "CIRCLE" and 1.0 <= e.dxf.radius <= 1.5]:
        cx, cy, r = d.dxf.center.x, d.dxf.center.y, d.dxf.radius
        around = near(cx, cy, 8)
        blades = []
        for e in around:
            if (
                e.dxftype() == "LWPOLYLINE"
                and len(e) == 2
                and not e.closed
                and e.dxf.get("const_width", 0) >= 0.25
            ):
                (ax, ay), (bx, by) = [(float(a), float(b)) for a, b in e.get_points("xy")]
                if 1.5 <= math.hypot(bx - ax, by - ay) <= 6:
                    blades.append(
                        (math.hypot((ax + bx) / 2 - cx, (ay + by) / 2 - cy), [(ax, ay), (bx, by)])
                    )
        if not blades:
            continue
        blade = sorted(blades)[0][1]
        segs = [blade]
        circles = [(cx, cy, r)]
        for e in around:
            if e.dxftype() == "CIRCLE" and 0.2 <= e.dxf.radius <= 0.45:
                circles.append((e.dxf.center.x, e.dxf.center.y, e.dxf.radius))
            elif e.dxftype() == "LINE" or (
                e.dxftype() == "LWPOLYLINE" and e.dxf.get("const_width", 0) < 0.25
            ):
                pts = _points(e)
                if pts and all(math.hypot(a - cx, b - cy) < 9 for a, b in pts):
                    segs.append(pts)
        symbols.append(
            {
                "handle": d.dxf.handle,
                "block": "EXPLODED",
                "layer": d.dxf.layer,
                "x": cx,
                "y": cy,
                "segs": segs,
                "circles": circles,
                "blade": blade,
                "vd": False,
                "ct": False,
                "square": False,
                "cod": "",
                "drive": (cx, cy),
                "on_load": on_load_hatch(around, (cx, cy), r),
            }
        )

    for s in symbols:
        s["state"] = blade_state(s)
        s["status"] = _drawing_status(
            s["layer"],
            s["drive"][0] if "drive" in s else s["x"],
            s["drive"][1] if "drive" in s else s["y"],
        )

    equipment = []
    for insert in msp.query("INSERT"):
        block = _original_block(doc, insert.dxf.name)
        if block not in (SECTION_INSULATOR_BLOCK, INSULATED_OVERLAP_BLOCK):
            continue
        m = insert.matrix44()
        pts = []
        for e in doc.blocks.get(insert.dxf.name):
            if not e.dxf.get("invisible", 0) and e.dxftype() in ("LWPOLYLINE", "LINE"):
                pts.extend((v.x, v.y) for v in (m.transform(Vec3(a, b, 0)) for a, b in _points(e)))
        cx = sum(p[0] for p in pts) / len(pts) if pts else insert.dxf.insert.x
        cy = sum(p[1] for p in pts) / len(pts) if pts else insert.dxf.insert.y
        equipment.append(
            {
                "handle": insert.dxf.handle,
                "block": block,
                "layer": insert.dxf.layer,
                "c": (cx, cy),
                "status": _drawing_status(insert.dxf.layer, cx, cy),
            }
        )

    lines = []
    for e in msp.query("LINE LWPOLYLINE"):
        pts = _points(e)
        if len(pts) >= 2:
            lines.append(
                {
                    "handle": e.dxf.handle,
                    "layer": e.dxf.layer,
                    "lt": e.dxf.get("linetype", "BYLAYER"),
                    "color": e.dxf.get("color", 256),
                    "w": e.dxf.get("const_width", 0) if e.dxftype() == "LWPOLYLINE" else 0,
                    "closed": bool(e.dxftype() == "LWPOLYLINE" and e.closed),
                    "pts": pts,
                }
            )

    texts = []
    for e in msp.query("TEXT MTEXT"):
        if e.dxftype() == "MTEXT":
            text, rot = e.plain_text(), e.get_rotation()
        else:
            text, rot = e.dxf.text, e.dxf.rotation
        # La caja se mide con el texto sin girar y se gira despues: la caja alineada a ejes de un
        # texto girado 111 grados no dice donde empieza ni donde acaba.
        flat = e.copy()
        if flat.dxftype() == "MTEXT":
            flat.set_rotation(0)
        else:
            flat.dxf.rotation = 0
        ins = e.dxf.insert
        obox = None
        try:
            box = bbox.extents([flat])
            if box.has_data:
                obox = (
                    rot,
                    ins.x,
                    ins.y,
                    box.extmin.x - ins.x,
                    box.extmax.x - ins.x,
                    box.extmin.y - ins.y,
                    box.extmax.y - ins.y,
                )
        except Exception:
            pass
        texts.append(
            {
                "handle": e.dxf.handle,
                "layer": e.dxf.layer,
                "text": text,
                "x": ins.x,
                "y": ins.y,
                "rot": rot,
                "obox": obox,
            }
        )
    return {"symbols": symbols, "equipment": equipment, "lines": lines, "texts": texts}


def blade_state(symbol):
    """CERRADO si la cuchilla esta alineada con sus dos contactos; ABIERTO si va inclinada."""
    blade = symbol.get("blade")
    if not blade:
        return ""
    (ax, ay), (bx, by) = blade
    small = [c for c in symbol["circles"] if 0.2 <= c[2] <= 0.45]
    ends = []
    for ex, ey in ((ax, ay), (bx, by)):
        ends.append(sorted((math.hypot(c[0] - ex, c[1] - ey), i) for i, c in enumerate(small))[:3])
    pair = next(((i0, i1) for _, i0 in ends[0] for _, i1 in ends[1] if i0 != i1), None)
    if not pair:
        return ""
    c0, c1 = small[pair[0]], small[pair[1]]
    axis = math.degrees(math.atan2(c1[1] - c0[1], c1[0] - c0[0]))
    blade_dir = math.degrees(math.atan2(by - ay, bx - ax))
    return "CERRADO" if angle_diff(axis, blade_dir) < CLOSED_BLADE_MAX_ANGLE else "ABIERTO"


# --------------------------------------------------------------------------------
# Rotulo <-> simbolo
# --------------------------------------------------------------------------------


def _segment_distance(x, y, symbol):
    best = 1e9
    for seg in symbol["segs"]:
        for a, b in zip(seg, seg[1:]):
            best = min(best, project(x, y, (a[0], a[1], b[0], b[1]))[0])
    for cx, cy, r in symbol["circles"]:
        d = math.hypot(x - cx, y - cy)
        best = min(best, abs(d - r), d if r < 0.5 else 1e9)
    if "drive" in symbol:
        # Las lineas de referencia de las zonas neutras se quedan a 3-4 unidades encima del circulo.
        best = min(best, max(0.0, math.hypot(symbol["drive"][0] - x, symbol["drive"][1] - y) - 2.5))
    return best


def match_labels(drawing):
    """Asocia cada nombre de rotulo a un simbolo.

    Tres pasos, en este orden: (1) la linea de referencia que sale del rotulo y termina en el
    simbolo; (2) asignacion global de coste minimo con el simbolo en la columna del rotulo
    (coste = distancia a lo largo de la lectura + 5 x distancia de traves); (3) la misma
    asignacion, mas permisiva, solo con lo que haya sobrado. La asignacion global evita que dos
    rotulos se queden el mismo simbolo, que es lo que pasaba con 'el mas cercano'.
    """
    import numpy as np
    from scipy.optimize import linear_sum_assignment

    labels, seen = [], set()
    for t in drawing["texts"]:
        if t["layer"] not in LABEL_LAYERS or not t["obox"]:
            continue
        key = (t["text"].strip(), round(t["x"]), round(t["y"]))
        if key in seen:  # el plano trae rotulos duplicados uno encima de otro
            continue
        seen.add(key)
        labels.append(
            dict(
                parse_label(t["text"], t["layer"]),
                handle=t["handle"],
                layer=t["layer"],
                text=t["text"],
                obox=t["obox"],
                x=t["x"],
                y=t["y"],
            )
        )
    devices = [l for l in labels if l["names"] and l["kind"] != "feeder"]
    symbols = drawing["symbols"]

    # 1) Lineas de referencia. Arrancan dentro del rotulo (o del extremo de su subrayado) y se
    #    siguen mientras empalmen con otra del mismo color y tipo de linea.
    leaders = [l for l in drawing["lines"] if l["layer"] in LEADER_LAYERS and not l["closed"]]
    endpoints = collections.defaultdict(list)
    for i, l in enumerate(leaders):
        for k in (0, -1):
            x, y = l["pts"][k]
            endpoints[(int(x // 4), int(y // 4))].append((i, k))

    def ends_near(x, y, r):
        found = []
        for gx in range(int((x - r) // 4), int((x + r) // 4) + 1):
            for gy in range(int((y - r) // 4), int((y + r) // 4) + 1):
                for i, k in endpoints.get((gx, gy), []):
                    px, py = leaders[i]["pts"][k]
                    if math.hypot(px - x, py - y) <= r:
                        found.append((i, k))
        return found

    def gap(obox, x, y):
        return max(outside(obox, x, y))

    def targets(label):
        obox = label["obox"]
        cx, cy = obox_center(obox)
        radius = max(obox[4] - obox[3], obox[6] - obox[5]) / 2 + 2
        starts = []
        touching = {
            i for i, k in ends_near(cx, cy, radius) if gap(obox, *leaders[i]["pts"][k]) <= 1.2
        }
        for i in touching:
            p0, p1 = leaders[i]["pts"][0], leaders[i]["pts"][-1]
            g0, g1 = gap(obox, *p0), gap(obox, *p1)
            if g0 <= 1.2 and g1 <= 1.2:  # el subrayado: mirar que empalma con sus puntas
                for p in (p0, p1):
                    for j, k in ends_near(p[0], p[1], 0.6):
                        far = leaders[j]["pts"][-1 if k == 0 else 0]
                        if j != i and gap(obox, *far) > 2.0:
                            starts.append((j, k))
            else:
                starts.append((i, 0 if g0 <= g1 else -1))
        found = []
        for i, k in starts:
            chain, current, kk = {i}, i, k
            for _ in range(6):
                far = leaders[current]["pts"][-1 if kk == 0 else 0]
                nxt = [
                    (j, k2)
                    for j, k2 in ends_near(far[0], far[1], 0.4)
                    if j not in chain
                    and leaders[j]["color"] == leaders[current]["color"]
                    and leaders[j]["lt"] == leaders[current]["lt"]
                ]
                if len(nxt) != 1:
                    break
                current, kk = nxt[0]
                chain.add(current)
            far = leaders[current]["pts"][-1 if kk == 0 else 0]
            if gap(obox, *far) > 2.0:
                found.append(far)
        return found

    sym_grid = collections.defaultdict(list)
    for j, s in enumerate(symbols):
        sym_grid[(int(s["x"] // 20), int(s["y"] // 20))].append(j)

    def symbols_near(x, y, r=25):
        found = []
        for gx in range(int((x - r) // 20), int((x + r) // 20) + 1):
            for gy in range(int((y - r) // 20), int((y + r) // 20) + 1):
                found.extend(sym_grid.get((gx, gy), []))
        return found

    match = {}
    for li, label in enumerate(devices):
        hits = []
        for x, y in targets(label):
            ranked = sorted((_segment_distance(x, y, symbols[j]), j) for j in symbols_near(x, y))
            if ranked and ranked[0][0] <= LEADER_HIT:
                hits.append(ranked[0])
        for k, (d, j) in enumerate(sorted(hits)[: len(label["names"])]):
            match[(li, k)] = {
                "sym": j,
                "how": "leader",
                "cost": 0.0,
                "du": 0.0,
                "dv": 0.0,
                "alt": None,
            }

    big = 1e6

    def assign(slots, free, max_dv, max_du, dv_weight, window, how):
        cost = np.full((len(slots), len(free)), big)
        parts = {}
        for a, (li, k) in enumerate(slots):
            label = devices[li]
            for b, j in enumerate(free):
                sx, sy = symbols[j]["drive"]
                if abs(sx - label["x"]) > window or abs(sy - label["y"]) > window:
                    continue
                du, dv = outside(label["obox"], sx, sy)
                if dv <= max_dv and du <= max_du:
                    cost[a, b] = du + dv_weight * dv + 0.01 * k
                    parts[(a, b)] = (du + dv_weight * dv, du, dv)
        if not slots or not free:
            return
        rows, cols = linear_sum_assignment(cost)
        for a, b in zip(rows, cols):
            if cost[a, b] >= big:
                continue
            ordered_row, ordered_col = np.sort(cost[a, :]), np.sort(cost[:, b])
            alt = min(
                ordered_row[1] if len(ordered_row) > 1 else big,
                ordered_col[1] if len(ordered_col) > 1 else big,
            )
            total, du, dv = parts[(a, b)]
            match[slots[a]] = {
                "sym": free[b],
                "how": how,
                "cost": total,
                "du": du,
                "dv": dv,
                "alt": float(alt) if alt < big and how == "aligned" else None,
            }

    taken = {m["sym"] for m in match.values()}
    slots = [
        (li, k)
        for li, l in enumerate(devices)
        for k in range(len(l["names"]))
        if (li, k) not in match
    ]
    free = [j for j, s in enumerate(symbols) if j not in taken and "drive" in s]
    assign(slots, free, 15, 60, 5, 120, "aligned")
    taken = {m["sym"] for m in match.values()}
    slots = [
        (li, k)
        for li, l in enumerate(devices)
        for k in range(len(l["names"]))
        if (li, k) not in match
    ]
    free = [j for j, s in enumerate(symbols) if j not in taken and "drive" in s]
    assign(slots, free, 30, 130, 3, 160, "relaxed")

    for li, label in enumerate(devices):
        label["match"] = [match.get((li, k)) for k in range(len(label["names"]))]
    return labels


def confidence(m):
    """A: linea de referencia, o en columna, cerca y sin rival. B: casi. C: lejos o permisivo."""
    if m["how"] == "leader":
        return "A"
    if m["how"] == "relaxed":
        return "C"
    margin = (m["alt"] - m["cost"]) if m["alt"] is not None else 99
    if m["dv"] <= 0.5 and m["du"] <= 30 and margin >= 5:
        return "A"
    if m["dv"] <= 2 and m["cost"] <= 35:
        return "B"
    return "C"


# --------------------------------------------------------------------------------
# Vias, numeros de via, agujas y estaciones del plano
# --------------------------------------------------------------------------------


class Tracks:
    """Las vias son polilineas con grosor en las capas de seccion de alimentacion (0_Has1...)."""

    GRID = 20.0

    def __init__(self, drawing):
        self.segs = []
        for l in drawing["lines"]:
            if l["w"] < 0.1 or l["closed"] or l["layer"] in NOT_TRACK_LAYERS:
                continue
            for a, b in zip(l["pts"], l["pts"][1:]):
                if a != b:
                    self.segs.append((a[0], a[1], b[0], b[1], l["handle"], l["layer"]))
        self.grid = collections.defaultdict(list)
        for i, s in enumerate(self.segs):
            for gx in range(
                int(min(s[0], s[2]) // self.GRID), int(max(s[0], s[2]) // self.GRID) + 1
            ):
                for gy in range(
                    int(min(s[1], s[3]) // self.GRID), int(max(s[1], s[3]) // self.GRID) + 1
                ):
                    self.grid[(gx, gy)].append(i)
        # '-1-', '-2-': el numero de via va encima de su linea. Se cuelga del segmento de debajo.
        self.numbers = []
        for t in drawing["texts"]:
            m = re.match(r"^-\s?(\d{1,3}[A-Z]?)\s?-$", t["text"].strip())
            if not m or not t["obox"]:
                continue
            cx, cy = obox_center(t["obox"])
            carrier = None
            for i in self.near(cx, cy, 8):
                d, tt, px, py = project(cx, cy, self.segs[i])
                if (
                    d <= 6
                    and 0.0 < tt < 1.0
                    and (py <= cy or seg_angle(self.segs[i]) > 10)
                    and (carrier is None or d < carrier[0])
                ):
                    carrier = (d, i)
            if carrier:
                self.numbers.append({"num": m.group(1), "x": cx, "y": cy, "seg": carrier[1]})
        self.number_grid = collections.defaultdict(list)
        for k, n in enumerate(self.numbers):
            self.number_grid[(int(n["x"] // 200), int(n["y"] // 200))].append(k)

    def near(self, x, y, r):
        found = set()
        for gx in range(int((x - r) // self.GRID), int((x + r) // self.GRID) + 1):
            for gy in range(int((y - r) // self.GRID), int((y + r) // self.GRID) + 1):
                found.update(self.grid.get((gx, gy), []))
        return found

    def nearest(self, x, y, r=1.5):
        best = None
        for i in self.near(x, y, r):
            d, t, px, py = project(x, y, self.segs[i])
            if d <= r and (best is None or d < best[0]):
                best = (d, i, px, py)
        return best

    def _collinear(self, i, j):
        a, b = self.segs[i], self.segs[j]
        if angle_diff(seg_angle(a), seg_angle(b)) > 2.5:
            return False
        length = math.hypot(a[2] - a[0], a[3] - a[1])
        ux, uy = (a[2] - a[0]) / length, (a[3] - a[1]) / length
        mx, my = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
        return abs(-(mx - a[0]) * uy + (my - a[1]) * ux) < 1.2

    def number_at(self, x, y, i, max_distance=700):
        """Numero de via del segmento i: el rotulo mas cercano colgado de la misma linea."""
        best = None
        for gx in range(int((x - max_distance) // 200), int((x + max_distance) // 200) + 1):
            for gy in range(int((y - 200) // 200), int((y + 200) // 200) + 1):
                for k in self.number_grid.get((gx, gy), []):
                    n = self.numbers[k]
                    d = math.hypot(n["x"] - x, n["y"] - y)
                    if d > max_distance:
                        continue
                    j = n["seg"]
                    if j == i or self.segs[j][4] == self.segs[i][4] or self._collinear(i, j):
                        if best is None or d < best[0]:
                            best = (d, n["num"])
        return best[1] if best else None


def read_switches(drawing):
    """'W31' con su '1:9' al lado. La tangente es el texto '1:N' mas cercano al final del codigo."""
    codes = re.compile(r"^(W\d+[A-Z]?)(\s*Switch)?$", re.I)
    rates = re.compile(r"^1:(\d+)\s*[A-Z]?$")
    rate_texts = [t for t in drawing["texts"] if rates.match(t["text"].strip())]
    switches = []
    for t in drawing["texts"]:
        if t["layer"] not in (
            "0_Turnouts Text",
            "FutureFreigthTerminal",
            "0_Non Electrified Track",
        ):
            continue
        m = codes.match(t["text"].strip())
        if not m or not t["obox"]:
            continue
        rot, ox, oy, u0, u1, v0, v1 = t["obox"]
        c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
        end = (ox + u1 * c, oy + u1 * s)
        best = None
        for r in rate_texts:
            d = math.hypot(r["x"] - end[0], r["y"] - end[1])
            if d < 4 and (best is None or d < best[0]):
                best = (d, int(rates.match(r["text"].strip()).group(1)))
        cx, cy = obox_center(t["obox"])
        switches.append(
            {"code": m.group(1).upper(), "x": cx, "y": cy, "tangent": best[1] if best else None}
        )
    return switches


def read_stations(drawing):
    """'BINYAMINA (BIN)\\n28' en la capa de estaciones: codigo, nombre y posicion.

    Una estacion rotulada sin codigo ('TZOMET REMEZ\\n30') solo cuenta si su nombre esta en
    PLAN_STATION_NAMES: el resto de esos textos son andenes, edificios y KP sueltos.
    """
    stations = []
    for t in drawing["texts"]:
        if t["layer"] != "0_Stations":
            continue
        m = re.search(r"\(([A-Z][A-Z_\-]{1,6})\)", t["text"].replace("\n", " "))
        if m:
            code, name = m.group(1), re.sub(r"\s+", " ", t["text"].split("(")[0]).strip()
        else:
            name = re.sub(r"\s+", " ", t["text"].strip().split("\n")[0]).strip()
            code = PLAN_STATION_NAMES.get(name.upper())
            if not code:
                continue
        stations.append({"code": code, "name": name, "x": t["x"], "y": t["y"]})
    return stations


# --------------------------------------------------------------------------------
# El maestro de perfiles
# --------------------------------------------------------------------------------


def read_profile_master(path):
    import openpyxl

    wb = openpyxl.load_workbook(path, read_only=True)

    def rows(name):
        it = wb[name].iter_rows(values_only=True)
        header = next(it)
        for row in it:
            yield dict(zip(header, row))

    stations = collections.defaultdict(set)
    for r in rows("STATIONS"):
        stations[r["EP"]].add(r["NOMBRE"])
    tracks = collections.defaultdict(list)
    for r in rows("TRACKS"):
        tracks[r["EP"]].append(
            {
                "name": r["NOMBRE"],
                "num": track_number_of(r["NOMBRE"]),
                "stations": [s.strip() for s in str(r["ESTACIONES"] or "").split("|") if s.strip()],
            }
        )
    profiles, ep_kp = [], collections.defaultdict(list)
    for r in rows("PROFILES"):
        if r["KP"] is None or r["EP"] == "RUBI":
            continue
        codes = [c for c in str(r["SECTIONING_FEEDING"] or "").split("|") if c]
        kp = float(r["KP"])
        ep_kp[r["EP"]].append(kp)
        profiles.append(
            {
                "ep": r["EP"],
                "via": r["VIA"],
                "profile": str(r["PROFILE_ID"]),
                "kp": kp,
                "track": track_number_of(r["VIA"]),
                "codes": codes,
                "dcodes": [c for c in codes if c.startswith(DISCONNECTOR_CODE_PREFIXES)],
                "scodes": [c for c in codes if c.startswith("SECT-I")],
            }
        )
    wb.close()
    station_ep = {code: ep for ep, codes in stations.items() for code in codes}
    ep_range = {ep: (min(v), max(v)) for ep, v in ep_kp.items()}
    return {
        "stations": stations,
        "station_ep": station_ep,
        "tracks": tracks,
        "profiles": profiles,
        "ep_range": ep_range,
    }


def read_disconnector_functions(path, profiles=()):
    """Los codigos del catalogo DisconnectorFunction habilitados, los de seccionador primero.

    Son las opciones de DISCONNECTOR_FUNCTION: el importador rechaza un codigo que no este
    habilitado en el catalogo. Primero Disc, LoadB y ED, que son los de un seccionador; detras, el
    resto del catalogo. Sin data/lov-master.xlsx, los codigos que ya usan los postes del maestro.
    """
    codes = []
    if path and os.path.exists(path):
        import openpyxl

        wb = openpyxl.load_workbook(path, read_only=True)
        try:
            it = wb["LOVS"].iter_rows(values_only=True)
            header = {h: i for i, h in enumerate(next(it))}
            for row in it:
                if (
                    row[header["ENTIDAD"]] == "DisconnectorFunction"
                    and str(row[header["ENABLED"]] or "").strip().upper() == "SI"
                    and row[header["CODIGO"]]
                ):
                    codes.append(str(row[header["CODIGO"]]).strip())
        finally:
            wb.close()
    else:
        codes = [c for p in profiles for c in p.get("codes", [])]
    unique = sorted(set(codes), key=str.lower)
    first = [c for c in unique if c.startswith(DISCONNECTOR_CODE_PREFIXES)]
    return first + [c for c in unique if c not in first]


# --------------------------------------------------------------------------------
# Seccionadores
# --------------------------------------------------------------------------------


def build_disconnectors(drawing, labels, tracks, plan_stations, master):
    import numpy as np
    from scipy.optimize import linear_sum_assignment

    symbols = drawing["symbols"]
    label_of = {}
    for label in labels:
        for k, m in enumerate(label.get("match") or []):
            if m:
                label_of[m["sym"]] = (label, k, m)
    equipment = [e for e in drawing["equipment"]]
    eq_grid = collections.defaultdict(list)
    for e in equipment:
        eq_grid[(int(e["c"][0] // 50), int(e["c"][1] // 50))].append(e)

    def equipment_near(x, y, r):
        found = []
        for gx in range(int((x - r) // 50), int((x + r) // 50) + 1):
            for gy in range(int((y - r) // 50), int((y + r) // 50) + 1):
                found.extend(
                    e
                    for e in eq_grid.get((gx, gy), [])
                    if math.hypot(e["c"][0] - x, e["c"][1] - y) <= r
                )
        return found

    plan_station = {s["code"]: s for s in plan_stations}
    records = []
    for j, s in enumerate(symbols):
        label, k, m = label_of.get(j, (None, None, None))
        name = label["names"][k] if label else ""
        # Las dos bolas rojas de las patas son donde el seccionador toca la catenaria.
        connections = []
        for x, y, r in [c for c in s["circles"] if 0.34 <= c[2] <= 0.45]:
            hit = tracks.nearest(x, y, 1.2)
            if hit:
                d, i, px, py = hit
                connections.append(
                    {
                        "pt": (px, py),
                        "seg": i,
                        "layer": tracks.segs[i][5],
                        "track": tracks.number_at(px, py, i),
                    }
                )
        bridged, bridged_handle = "", ""
        if len(connections) >= 2:
            (x1, y1), (x2, y2) = connections[0]["pt"], connections[1]["pt"]
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            half = math.hypot(x2 - x1, y2 - y1) / 2
            best = None
            for e in equipment_near(mx, my, half + 1.5):
                ux, uy = x2 - x1, y2 - y1
                length2 = ux * ux + uy * uy
                if length2 == 0:
                    continue
                t = ((e["c"][0] - x1) * ux + (e["c"][1] - y1) * uy) / length2
                if -0.05 <= t <= 1.05:
                    d = math.hypot(e["c"][0] - mx, e["c"][1] - my)
                    if best is None or d < best[0]:
                        best = (d, e)
            if best:
                bridged = "IO" if best[1]["block"] == INSULATED_OVERLAP_BLOCK else "SI"
                bridged_handle = best[1]["handle"]
        x, y = s.get("drive", (s["x"], s["y"]))
        records.append(
            {
                "handle": s["handle"],
                "block": s["block"],
                "type": SYMBOL_TYPE.get(s["block"], s["block"]),
                "layer": s["layer"],
                "x": x,
                "y": y,
                "status": s["status"],
                "name": name,
                "label_text": label["text"].replace("\n", " | ") if label else "",
                "kp_txt": label["kp_txt"] if label else None,
                "kp_m": label["kp_m"] if label else None,
                "names_in_label": len(label["names"]) if label else 0,
                "how": m["how"] if m else "",
                "confidence": confidence(m) if m else "",
                "on_load": s["on_load"],
                "state": s["state"],
                "has_drive": "drive" in s,
                "vd": s["vd"],
                "ct": s["ct"],
                "rtu_square": s["square"] and s["block"] != "DISCONNECTOR",
                "cod": s["cod"],
                "prefix": station_code(name),
                "plan_station": plan_station.get(station_code(name), {}).get("name", ""),
                "tracks": sorted({c["track"] for c in connections if c["track"]}),
                "sections": sorted({c["layer"] for c in connections}),
                "bridged": bridged,
                "bridged_handle": bridged_handle,
                "name_function": function_from_name(name),
            }
        )

    # EP: por el prefijo, si es una estacion del maestro; si no, por los vecinos ya situados cuyo
    # EP cubre el KP. Los de lineas que el maestro no tiene se quedan sin EP, y los del deposito de
    # Haifa tambien, aunque un vecino de fuera del recuadro cubra su KP.
    for r in records:
        r["ep"] = None if future_ep(r["status"]) else master["station_ep"].get(r["prefix"])
        r["ep_from"] = "prefijo" if r["ep"] else ""
    placed = [r for r in records if r["ep"]]
    for r in records:
        if r["ep"] or r["kp_m"] is None or future_ep(r["status"]):
            continue
        for d, ep in sorted(
            (math.hypot(o["x"] - r["x"], o["y"] - r["y"]), o["ep"])
            for o in placed
            if abs(o["x"] - r["x"]) < 600 and abs(o["y"] - r["y"]) < 300
        ):
            lo, hi = master["ep_range"][ep]
            if lo - 300 <= r["kp_m"] <= hi + 300:
                r["ep"], r["ep_from"] = ep, "vecinos"
                break
    # Sin KP (un rotulo 'KP47+XXX', o un simbolo sin rotulo) no hay rango que comprobar: el EP es
    # el de los tres vecinos situados mas cercanos, si los tres dicen el mismo y el primero esta
    # cerca. Es lo que mete en su EP la zona neutra futura de Remez y la via no electrificada de
    # al lado, entre Binyamina y Hadera.
    placed = [r for r in records if r["ep"]]
    for r in records:
        if r["ep"] or r["kp_m"] is not None or future_ep(r["status"]):
            continue
        near = sorted(
            (math.hypot(o["x"] - r["x"], o["y"] - r["y"]), o["ep"])
            for o in placed
            if abs(o["x"] - r["x"]) < 600 and abs(o["y"] - r["y"]) < 300
        )[:3]
        if len(near) == 3 and near[0][0] < 400 and len({ep for _, ep in near}) == 1:
            r["ep"], r["ep_from"] = near[0][1], "vecinos sin KP"

    # Poste: asignacion 1:1 por EP contra los postes con codigo de seccionador, con el coste de
    # pole_cost. Los de alimentacion no compiten: van en el portico de la subestacion, y con el
    # poste mas cercano le quitaban el suyo a uno de linea. Es la propuesta: desde V29 un poste
    # admite varios seccionadores, pero casi siempre lleva uno, y el reparto 1:1 es lo que evita
    # que dos se queden con el poste mas cercano. El segundo de un poste se pone a mano.
    by_ep = collections.defaultdict(list)
    for i, r in enumerate(records):
        if r["ep"] and r["kp_m"] is not None and not on_portal(r):
            by_ep[r["ep"]].append(i)
    poles_by_ep = collections.defaultdict(list)
    for p in master["profiles"]:
        if p["dcodes"]:
            poles_by_ep[p["ep"]].append(p)
    for ep, idx in by_ep.items():
        poles = poles_by_ep.get(ep, [])
        if not poles:
            continue
        cost = np.full((len(idx), len(poles)), 1e6)
        for a, i in enumerate(idx):
            r = records[i]
            for b, p in enumerate(poles):
                c = pole_cost(r, p)
                if c is not None:
                    cost[a, b] = c
        rows, cols = linear_sum_assignment(cost)
        for a, b in zip(rows, cols):
            if cost[a, b] < 1e6:
                r, p = records[idx[a]], poles[b]
                r["pole"] = p
                r["pole_dkp"] = p["kp"] - r["kp_m"]

    # Sin poste con codigo: el poste mas cercano de una via compatible, si esta a menos de 30 m.
    # El maestro trae el poste pero sin el seccionador en SECTIONING_FEEDING (lleva FS-1, o nada).
    # No a los de puesta a tierra ni a los de alimentacion: sin un poste ED, o en el portico de la
    # subestacion, lo normal es que no esten en un poste, y el mas cercano seria una pista falsa.
    profiles_by_ep = collections.defaultdict(list)
    for p in master["profiles"]:
        profiles_by_ep[p["ep"]].append(p)
    for r in records:
        if r.get("pole") or not r["ep"] or r["kp_m"] is None or earthing(r) or on_portal(r):
            continue
        candidates = sorted(
            (
                (abs(p["kp"] - r["kp_m"]), n)
                for n, p in enumerate(profiles_by_ep[r["ep"]])
                if not r["tracks"] or not p["track"] or p["track"] in r["tracks"]
            )
        )
        if candidates:
            d, n = candidates[0]
            r["nearest_pole"] = profiles_by_ep[r["ep"]][n]
            r["nearest_pole_d"] = d

    # Estacion: el prefijo si es estacion del maestro en ese EP; si no, la estacion del plano mas
    # cercana entre las que el maestro tiene en ese EP (un seccionador de plena via, de una zona
    # neutra, no lleva el codigo de ninguna estacion en el nombre).
    for r in records:
        if not r["ep"]:
            r["station"], r["station_from"] = "", ""
            continue
        codes = master["stations"].get(r["ep"], set())
        if r["prefix"] in codes:
            r["station"], r["station_from"] = r["prefix"], "prefijo"
            continue
        nearest = sorted(
            (math.hypot(s["x"] - r["x"], s["y"] - r["y"]), STATION_ALIAS.get(s["code"], s["code"]))
            for s in plan_stations
            if STATION_ALIAS.get(s["code"], s["code"]) in codes
        )
        if nearest and nearest[0][0] < 800:
            r["station"], r["station_from"] = nearest[0][1], "cercania"
        else:
            r["station"], r["station_from"] = "", ""
    return records


# --------------------------------------------------------------------------------
# Aisladores de seccion
# --------------------------------------------------------------------------------


def build_insulators(drawing, tracks, switches, plan_stations, disconnectors, master):
    import numpy as np
    from scipy.optimize import linear_sum_assignment

    bridging = collections.defaultdict(list)
    for r in disconnectors:
        if r["bridged"] == "SI" and r["bridged_handle"]:
            bridging[r["bridged_handle"]].append(r)
    sw_grid = collections.defaultdict(list)
    for s in switches:
        sw_grid[(int(s["x"] // 30), int(s["y"] // 30))].append(s)

    def switches_near(x, y, r):
        """Agujas rotuladas a menos de r del cruce, de la mas cercana a la mas lejana."""
        found = []
        for gx in range(int((x - r) // 30), int((x + r) // 30) + 1):
            for gy in range(int((y - r) // 30), int((y + r) // 30) + 1):
                found.extend(
                    (math.hypot(s["x"] - x, s["y"] - y), id(s), s)
                    for s in sw_grid.get((gx, gy), [])
                    if math.hypot(s["x"] - x, s["y"] - y) <= r
                )
        return [(d, s) for d, _, s in sorted(found)]

    def dominant_angle(x, y, r=150):
        hist = collections.Counter()
        for i in tracks.near(x, y, r):
            s = tracks.segs[i]
            hist[round(seg_angle(s)) % 180] += math.hypot(s[2] - s[0], s[3] - s[1])
        return hist.most_common(1)[0][0] if hist else None

    labelled = [r for r in disconnectors if r["kp_m"] is not None and not future_ep(r["status"])]
    records = []
    for e in [e for e in drawing["equipment"] if e["block"] == SECTION_INSULATOR_BLOCK]:
        cx, cy = e["c"]
        r = {
            "handle": e["handle"],
            "x": cx,
            "y": cy,
            "layer": e["layer"],
            "status": e["status"],
            "type": "",
            "tracks": [],
            "switches": [],
            "switches_near": [],
            "carrier_track": None,
            "section": "",
        }
        r["station"], r["station_from"], r["plan_station"], r["kp_estimate"] = "", "", "", None
        records.append(r)
        hit = tracks.nearest(cx, cy, 1.5)
        if not hit:
            continue
        d, i, px, py = hit
        seg = tracks.segs[i]
        a = seg_angle(seg)
        dom = dominant_angle(cx, cy)
        r["carrier_track"] = tracks.number_at(px, py, i)
        r["section"] = seg[5]
        if dom is not None and angle_diff(a, dom) > 3:
            # En un escape: la primera via que corta la linea del escape a cada lado del aislador.
            # La linea esta partida en el aislador, asi que se prolonga hasta 40 unidades.
            r["type"] = "TRACK_CONNECTION"
            length = math.hypot(seg[2] - seg[0], seg[3] - seg[1])
            ux, uy = (seg[2] - seg[0]) / length, (seg[3] - seg[1]) / length
            hits = []
            for j in tracks.near(cx, cy, 45):
                other = tracks.segs[j]
                if angle_diff(seg_angle(other), a) <= 3:
                    continue
                vx, vy = other[2] - other[0], other[3] - other[1]
                den = ux * vy - uy * vx
                if abs(den) < 1e-9:
                    continue
                sx, sy = other[0] - cx, other[1] - cy
                along = (sx * vy - sy * vx) / den
                tq = (sx * uy - sy * ux) / den
                if -0.02 <= tq <= 1.02 and 0.3 < abs(along) <= 40:
                    qx, qy = cx + along * ux, cy + along * uy
                    hits.append((along, tracks.number_at(qx, qy, j), qx, qy))
            sides = (
                sorted([h for h in hits if h[0] < 0], key=lambda h: -h[0])[:1]
                + sorted([h for h in hits if h[0] > 0], key=lambda h: h[0])[:1]
            )
            r["tracks"] = [h[1] for h in sides]
            for h in sides:
                near = switches_near(h[2], h[3], 10)
                r["switches"].append(near[0][1] if near else None)
                # Dos escapes que arrancan del mismo punto ponen sus dos rotulos juntos (W3 y W4 en
                # Binyamina). Solo se avisa cuando el segundo esta casi tan cerca como el primero.
                rivals = [s for d, s in near[1:3] if d - near[0][0] < SWITCH_TIE] if near else []
                r["switches_near"].append(
                    list(dict.fromkeys([near[0][1]["code"]] + [s["code"] for s in rivals]))
                    if near
                    else []
                )
        else:
            r["type"] = "IN_TRACK"
            r["tracks"] = [r["carrier_track"]] if r["carrier_track"] else []
        bridges = bridging.get(e["handle"], [])
        if bridges:
            b = sorted(bridges, key=lambda x: x["confidence"])[0]
            r.update(
                kp_m=b["kp_m"],
                kp_txt=b["kp_txt"],
                kp_from="seccionador que lo puentea (%s)" % (b["name"] or b["handle"]),
                bridged_by=", ".join(x["name"] or x["handle"] for x in bridges),
                ep=None if future_ep(r["status"]) else b["ep"],
                prefix=b["prefix"],
            )
        # EP y estacion por los seccionadores rotulados de alrededor; KP estimado entre los dos
        # vecinos de la misma banda horizontal.
        around = sorted(
            (
                (math.hypot(o["x"] - cx, o["y"] - cy), n)
                for n, o in enumerate(labelled)
                if abs(o["x"] - cx) < 400 and abs(o["y"] - cy) < 200
            )
        )
        if not r.get("ep") and around and not future_ep(r["status"]):
            # El del seccionador rotulado mas cercano, aunque no tenga EP: si ese esta en una linea
            # que el maestro no tiene, el aislador tambien. Saltar al siguiente con EP mezclaria
            # lineas.
            nearest = labelled[around[0][1]]
            r["ep"], r["prefix"] = nearest["ep"], nearest["prefix"]
        band = [
            (o["x"], o["kp_m"])
            for o in labelled
            if abs(o["y"] - cy) < 40
            and abs(o["x"] - cx) < 400
            and (not r.get("ep") or o["ep"] == r.get("ep"))
        ]
        r["kp_estimate"] = interpolate_kp(cx, band)
        stations = sorted(
            ((math.hypot(s["x"] - cx, s["y"] - cy), s) for s in plan_stations), key=lambda t: t[0]
        )
        codes = master["stations"].get(r.get("ep"), set())
        r["plan_station"] = (
            "%s (%s)" % (stations[0][1]["name"], stations[0][1]["code"])
            if stations and stations[0][0] < 400
            else ""
        )
        r["plan_station_code"] = (
            STATION_ALIAS.get(stations[0][1]["code"], stations[0][1]["code"])
            if stations and stations[0][0] < 400
            else ""
        )
        in_master = [
            s for d, s in stations if STATION_ALIAS.get(s["code"], s["code"]) in codes and d < 800
        ]
        prefix = STATION_ALIAS.get(r.get("prefix") or "", r.get("prefix") or "")
        if prefix in codes:
            r["station"], r["station_from"] = prefix, "prefijo"
        elif in_master:
            r["station"], r["station_from"] = (
                STATION_ALIAS.get(in_master[0]["code"], in_master[0]["code"]),
                "cercania",
            )
        else:
            r["station"], r["station_from"] = "", ""

    # Poste SECT-I del maestro, 1:1 por EP, solo para los aisladores con KP de verdad.
    poles = [p for p in master["profiles"] if p["scodes"]]
    by_ep = collections.defaultdict(list)
    for i, r in enumerate(records):
        if r.get("kp_m") is not None and r.get("ep"):
            by_ep[r["ep"]].append(i)
    for ep, idx in by_ep.items():
        candidates = [p for p in poles if p["ep"] == ep]
        if not candidates:
            continue
        cost = np.full((len(idx), len(candidates)), 1e6)
        for a, i in enumerate(idx):
            r = records[i]
            wanted = [t for t in r["tracks"] if t]
            for b, p in enumerate(candidates):
                dk = abs(p["kp"] - r["kp_m"])
                if dk <= SECT_I_MATCH_MAX_M:
                    cost[a, b] = dk + (
                        60 if wanted and p["track"] and p["track"] not in wanted else 0
                    )
        rows, cols = linear_sum_assignment(cost)
        for a, b in zip(rows, cols):
            if cost[a, b] < 1e6:
                records[idx[a]]["sect_i_pole"] = candidates[b]
                candidates[b]["used"] = True
    unused = [p for p in poles if not p.get("used")]
    return records, unused


# --------------------------------------------------------------------------------
# Libro de revision
# --------------------------------------------------------------------------------

DISCONNECTOR_COLUMNS = [
    "EP",
    "ESTACION",
    "VIA",
    "VIA_CONECTADA",
    "PROFILE_ID",
    "KP_POSTE",
    "NOMBRE",
    "KP",
    "ON_LOAD",
    "NORMALLY_OPEN",
    "DRIVE_TYPE",
    "DISCONNECTOR_FUNCTION",
    "ENABLED",
]
# Las agujas van en la fila de su aislador, para no escribir dos veces el aislador y sus agujas: la
# 1 es la del lado de VIA y la 2 la del lado de VIA_CONECTADA. build_profile_master.py escribe con
# ellas la hoja SECTION_INSULATOR_SWITCHES del maestro.
INSULATOR_SWITCH_COLUMNS = [
    ("AGUJA_1", "KP_AGUJA_1", "TANGENTE_1"),
    ("AGUJA_2", "KP_AGUJA_2", "TANGENTE_2"),
]
INSULATOR_COLUMNS = (
    ["EP", "ESTACION", "NOMBRE", "KP", "TIPO_INSTALACION", "VIA", "VIA_CONECTADA"]
    + [column for switch in INSULATOR_SWITCH_COLUMNS for column in switch]
    + ["ENABLED"]
)
# Dos agujas a mas de 300 m: la segunda va con el KP de otra via, y el del aislador es el de la
# primera. La misma regla que build_profile_master.insulator_kp, que la vuelve a aplicar al cargar.
SWITCH_KP_MAX_GAP_M = 300

# Cuando una fila esta revisada. Lo dice una sola celda, REVISAR, y un solo valor, NO: el
# generador la escribe en SI con su motivo en MOTIVO_REVISAR (o en NO, si no tiene nada que
# preguntar), y quien revisa la fila contra el plano la pasa a NO. Cualquier otra cosa es
# pendiente: SI, y tambien vacia, que es como llega una fila añadida a mano y como quedaria un
# olvido. ENABLED no lo dice: decide si la fila se carga, y una fila puede cargarse con una duda
# abierta (una nota que no bloquea) o estar revisada y no cargarse.
REVIEW_SHEETS = ("DISCONNECTORS", "SECTION_INSULATORS")
REVIEWED, TO_REVIEW, UNMARKED = "REVISADA", "PENDIENTE", "SIN MARCAR"


def flag(value):
    """Una celda SI/NO como la lee el importador: sin espacios y en mayusculas; vacia, ''."""
    return "" if value is None else str(value).strip().upper()


def review_state(row):
    """REVISADA (REVISAR = NO), PENDIENTE (REVISAR = SI) o SIN MARCAR (vacia u otro valor).

    Solo REVISADA cuenta como revisada: las otras dos salen en la hoja PENDIENTE.
    """
    value = flag(row.get("REVISAR"))
    if value == "NO":
        return REVIEWED
    return TO_REVIEW if value == "SI" else UNMARKED


def is_reviewed(row):
    return review_state(row) == REVIEWED


# Motivos que dejan la fila en ENABLED = NO aunque tenga todos los datos.
BLOCKING = (
    "el poste esta a",
    "el DXF dice",
    "etiqueta con",
    "rotulo lejos",
    "el maestro repite",
    "poste propuesto",
    "estacion propuesta",
    "funcion propuesta",
    "simbolo sin rotulo",
    "via sin comprobar",
    "sin poste en el maestro",
)


def nearby_poles(profiles_of_ep, r):
    """Los postes del EP a menos de POLE_MATCH_MAX_M del KP del rotulo, del mas cercano al mas
    lejano.

    Son las opciones del desplegable de PROFILE_ID y de KP_POSTE, y lo que cuenta POSTES_CERCANOS:
    sin ellos, cambiar el poste propuesto obligaba a buscarlo a mano en el maestro.
    """
    if r["kp_m"] is None:
        return []
    near = [p for p in profiles_of_ep if abs(p["kp"] - r["kp_m"]) <= POLE_MATCH_MAX_M]
    near.sort(key=lambda p: (abs(p["kp"] - r["kp_m"]), str(p["via"] or ""), str(p["profile"])))
    return near[:POLE_OPTIONS_MAX]


def kp_text(kp):
    """Un KP en metros como se escribe: sin ceros de mas (93453, 93453.41)."""
    return ("%.3f" % kp).rstrip("0").rstrip(".")


def pole_options(poles, current=None):
    """Las opciones de PROFILE_ID y de KP_POSTE: el poste de la fila primero, y sin repetir.

    El de la fila puede estar mas lejos que los cercanos: su KP no es siempre el del rotulo. Los
    KP van como numeros, igual que en la columna: escritos como texto, un Excel con coma decimal
    no leeria "93453.41" como un numero.
    """
    ids, kps = [], []
    for p in ([current] if current else []) + list(poles):
        if p["profile"] not in ids:
            ids.append(p["profile"])
        if p["kp"] not in kps:
            kps.append(p["kp"])
    return {"PROFILE_ID": ids, "KP_POSTE": kps}


def describe_poles(poles, kp_m):
    """POSTES_CERCANOS: cada poste con su via, su KP, la distancia al rotulo y sus codigos."""
    return " | ".join(
        "%s (%s, kp %s, a %.0f m%s)"
        % (
            p["profile"],
            p["via"],
            kp_text(p["kp"]),
            abs(p["kp"] - kp_m),
            ", " + "|".join(p.get("dcodes") or []) if p.get("dcodes") else "",
        )
        for p in poles
    )


def orphan_name_options(rows, orphans):
    """El nombre de un simbolo sin rotulo: los rotulos sueltos de al lado, del mas cercano.

    El plano deja rotulos sin simbolo (ROTULOS_SIN_SIMBOLO) y simbolos sin rotulo; cuando estan
    cerca, lo normal es que sean el mismo seccionador, y el desplegable de NOMBRE los ofrece.
    """
    for row in rows:
        if row["NOMBRE"]:
            continue
        near = sorted(
            (math.hypot(o["X"] - row["X"], o["Y"] - row["Y"]), o["NOMBRE"]) for o in orphans
        )
        names = [name for d, name in near if d <= ORPHAN_LABEL_MAX_DIST and name]
        if names:
            row.setdefault("_options", {})["NOMBRE"] = names


def disconnector_rows(records, master):
    rows, outside = [], []
    profiles_by_ep = collections.defaultdict(list)
    for p in master.get("profiles", []):
        profiles_by_ep[p["ep"]].append(p)
    for r in records:
        if future_ep(r["status"]):
            continue
        reasons, proposals = [], set()
        pole = r.get("pole")
        if not r["ep"]:
            outside.append(r)
            continue
        if r["status"] != EN_SERVICE:
            reasons.append(
                "dibujado como %s (capa %s): entra porque esta en un EP del maestro"
                % (r["status"], r["layer"])
            )
        if r["ep_from"] == "vecinos sin KP":
            reasons.append("EP de los seccionadores de al lado: el rotulo no tiene KP")
        via = pole["via"] if pole else ""
        profile = pole["profile"] if pole else ""
        chosen = pole  # el poste de la fila: el casado o, sin el, el propuesto
        if not pole and r.get("nearest_pole") and r["nearest_pole_d"] <= POLE_PROPOSAL_MAX_M:
            near = chosen = r["nearest_pole"]
            via, profile = near["via"], near["profile"]
            # KP_POSTE va con PROFILE_ID: en una via que repite el identificador, es lo que dice
            # cual de los dos es, y aceptar la propuesta no deberia dejarlo a medias.
            proposals.update({"VIA", "PROFILE_ID", "KP_POSTE"})
            reasons.append(
                "poste propuesto: el mas cercano por KP (a %.0f m), sin seccionador en "
                "SECTIONING_FEEDING (%s)"
                % (r["nearest_pole_d"], "|".join(near["codes"]) or "vacio")
            )
        elif not pole and on_portal(r):
            reasons.append(
                "sin poste por ser de alimentacion (FP): va en el portico de la subestacion"
            )
        elif not pole and earthing(r):
            reasons.append(
                "sin poste por ser de puesta a tierra: el maestro no tiene un poste ED cerca"
            )
        elif not pole:
            far = (
                (" (el mas cercano esta a %.0f m)" % r["nearest_pole_d"])
                if r.get("nearest_pole")
                else ""
            )
            # El poste es opcional: hay seccionadores que no estan en uno. Pero el generador no
            # distingue eso de un poste que no ha sabido encontrar, asi que lo decide una persona.
            reasons.append(
                "sin poste en el maestro cerca del KP%s: si no esta en un poste, deja PROFILE_ID "
                "vacio y pon ENABLED = SI" % far
            )
        station = r["station"]
        if r["station_from"] == "cercania":
            proposals.add("ESTACION")
            reasons.append("estacion propuesta: la del plano mas cercana")
        if not via and r["tracks"]:
            via, why = master_track(master["tracks"].get(r["ep"], []), station, r["tracks"][0])
            if why:
                reasons.append(why)
        function = pole["dcodes"][0] if pole else ""
        # La B del by-pass con las patas en dos vias del plano: pone esas dos vias en paralelo.
        two_tracks = len(set(r["tracks"])) >= 2
        bypass_across = two_tracks and r["name_function"] in BYPASS_NAME_FUNCTIONS
        if not function:
            function = function_proposal(
                r["type"], r["on_load"], r["name_function"], r["bridged"], parallel=bypass_across
            )
            if function:
                proposals.add("DISCONNECTOR_FUNCTION")
                reasons.append("funcion propuesta desde el plano (%s)" % function)
        # VIA_CONECTADA (V27): la otra via de uno que pone dos en paralelo. No bloquea ENABLED: la
        # propuesta sale de las patas del plano, y si falta se pinta en amarillo para rellenarla.
        connected, missing = "", set()
        if bypass_across or PARALLEL_FUNCTION in function:
            if two_tracks:
                connected, why = connected_track(
                    r, via, master["tracks"].get(r["ep"], []), station, on_a_pole=bool(chosen)
                )
            else:
                why = "el plano no dice con que via"
            if connected:
                proposals.add("VIA_CONECTADA")
                reasons.append(
                    "via conectada propuesta: el plano une las vias %s" % "/".join(r["tracks"])
                )
            else:
                missing.add("VIA_CONECTADA")
                reasons.append(
                    "pone dos vias en paralelo: escribe la otra en VIA_CONECTADA (%s)" % why
                )
        if not r["name"]:
            reasons.append("simbolo sin rotulo: falta el nombre")
        if r["kp_m"] is None and r["name"]:
            reasons.append("rotulo sin KP (%s)" % (r["kp_txt"] or "ninguno"))
        if r["names_in_label"] > 1:
            reasons.append("etiqueta con %d nombres: comprobar cual es cual" % r["names_in_label"])
        validated = pole is not None and abs(r["pole_dkp"]) <= 10
        if r["confidence"] == "C" and not validated:
            reasons.append("rotulo lejos del simbolo")
        if pole and (not r["tracks"] or pole["track"] not in r["tracks"]):
            # El plano solo numera las vias en las estaciones. En plena via, con dos postes iguales
            # al mismo KP (uno por via), la asignacion no tiene con que elegir: se dice. Lo mismo
            # cuando la via del plano no es la de ninguno de los dos.
            twins = [
                p
                for p in master["profiles"]
                if p["ep"] == pole["ep"]
                and p["dcodes"]
                and p["via"] != pole["via"]
                and abs(p["kp"] - pole["kp"]) <= 5
            ]
            if twins:
                reasons.append(
                    "via sin comprobar: %s y el maestro tiene otro poste al mismo KP en %s"
                    % (
                        (
                            "el plano dice via %s, que no es la del poste" % "/".join(r["tracks"])
                            if r["tracks"]
                            else "el plano no numera la via aqui"
                        ),
                        ", ".join("%s (%s)" % (t["via"], t["profile"]) for t in twins[:3]),
                    )
                )
        if pole:
            if abs(r["pole_dkp"]) > 30:
                reasons.append("el poste esta a %.0f m del KP del rotulo" % r["pole_dkp"])
            if r["tracks"] and pole["track"] and pole["track"] not in r["tracks"]:
                # La asignacion lo penaliza pero no lo prohibe: sin otro poste cerca, casa igual. La
                # via del plano falla mas que el KP (numera las vias de la estacion, no las del
                # maestro), asi que con el KP casado a 10 m o menos es una nota y no un bloqueo.
                via_dxf = "/".join(r["tracks"])
                if validated:
                    reasons.append(
                        "via distinta: el DXF dice %s y el poste esta en la %s, con el KP a %.0f m"
                        % (via_dxf, pole["track"], abs(r["pole_dkp"]))
                    )
                else:
                    reasons.append(
                        "el DXF dice via %s y el poste esta en la via %s" % (via_dxf, pole["track"])
                    )
            load_break = any(x.startswith("LoadB") for x in pole["dcodes"])
            if r["type"] == "SECCIONADOR" and load_break != r["on_load"]:
                reasons.append(
                    "el DXF dice %s y el poste %s"
                    % ("on-load" if r["on_load"] else "off-load", "|".join(pole["dcodes"]))
                )
        if not station:
            reasons.append("estacion sin determinar")
        if not function:
            reasons.append("funcion sin determinar")
        state = normally_open(r["state"])
        if not state:
            reasons.append("estado normal sin determinar: el plano no deja ver la cuchilla")
        drive = drive_type(r["has_drive"])
        if not drive:
            reasons.append("accionamiento sin determinar: el simbolo no lleva el circulo del motor")
        # Ni el poste ni el estado normal ni el accionamiento son obligatorios en Disconnector.
        complete = all([r["ep"], station, r["name"], function])
        enabled = "SI" if complete and not any(x.startswith(BLOCKING) for x in reasons) else "NO"
        nearby = nearby_poles(profiles_by_ep[r["ep"]], r)
        rows.append(
            {
                "EP": r["ep"],
                "ESTACION": station,
                "VIA": via,
                "VIA_CONECTADA": connected,
                "PROFILE_ID": profile,
                # El KP del poste: solo lo mira el importador cuando la via repite el PROFILE_ID
                # (dos tramos concatenados, como EP9A), y entonces es lo unico que los distingue.
                "KP_POSTE": (chosen or {}).get("kp", ""),
                "NOMBRE": r["name"],
                # El KP propio, solo sin poste: con poste es el del perfil (V26).
                "KP": "" if profile or r["kp_m"] is None else r["kp_m"],
                "ON_LOAD": "SI" if r["on_load"] else "NO",
                "NORMALLY_OPEN": state,
                "DRIVE_TYPE": drive,
                "DISCONNECTOR_FUNCTION": function,
                "ENABLED": enabled,
                "REVISAR": "SI" if reasons else "NO",
                "MOTIVO_REVISAR": "; ".join(reasons),
                "KP_ROTULO": r["kp_txt"] or "",
                "KP_ROTULO_M": r["kp_m"] if r["kp_m"] is not None else "",
                "DIF_KP_M": round(r["pole_dkp"], 1) if pole else "",
                "CODIGO_POSTE": "|".join(pole["dcodes"]) if pole else "",
                "POSTES_CERCANOS": describe_poles(nearby, r["kp_m"]) if nearby else "",
                "TIPO_DXF": r["type"],
                "ESTADO_DIBUJO": r["status"],
                "PREFIJO": r["prefix"],
                "DETECTOR_TENSION": "SI" if r["vd"] else "NO",
                "PUENTEA": r["bridged"],
                "VIAS_DXF": ", ".join(r["tracks"]),
                "SECCION_ALIMENTACION": ", ".join(r["sections"]),
                "ROTULO_DXF": r["label_text"],
                "ASOCIACION": r["how"],
                "CONFIANZA": r["confidence"],
                "EP_ORIGEN": r["ep_from"],
                "X": round(r["x"], 2),
                "Y": round(r["y"], 2),
                "HANDLE": r["handle"],
                "_proposals": proposals,
                "_missing": missing,
                "_options": pole_options(nearby, chosen) if nearby else {},
            }
        )

    # Un mismo poste fisico aparece en dos hojas de via del maestro (mismo PROFILE_ID y KP): los
    # dos seccionadores casados con el se señalan, porque o es el mismo dibujado en las dos hojas
    # (y uno sobra) o el poste lleva los dos, que desde V29 se puede. Lo decide una persona.
    # Solo los casados: un poste propuesto ya espera a una persona.
    def matched(d):
        return d["PROFILE_ID"] and d["KP_POSTE"] != "" and "PROFILE_ID" not in d["_proposals"]

    twins = collections.Counter(
        (d["EP"], d["PROFILE_ID"], d["KP_POSTE"]) for d in rows if matched(d)
    )
    for d in rows:
        key = (d["EP"], d["PROFILE_ID"], d["KP_POSTE"])
        if matched(d) and twins[key] > 1:
            others = [
                o["NOMBRE"]
                for o in rows
                if o is not d and matched(o) and (o["EP"], o["PROFILE_ID"], o["KP_POSTE"]) == key
            ]
            d["MOTIVO_REVISAR"] = "; ".join(
                x
                for x in [
                    d["MOTIVO_REVISAR"],
                    "el maestro repite el poste %s en otra via; tambien casado con %s: si es el "
                    "mismo seccionador, uno sobra; si el poste lleva los dos, valen los dos"
                    % (d["PROFILE_ID"], ", ".join(others)),
                ]
                if x
            )
            d["REVISAR"], d["ENABLED"] = "SI", "NO"

    # El importador identifica cada seccionador por su EP y su nombre, sin mayusculas: de dos filas
    # con el mismo nombre en el mismo EP solo cargaria la primera. Se señalan las dos, porque no se
    # sabe cual sobra, o si a una le falta algo en el nombre.
    def name_key(d):
        name = str(d["NOMBRE"] or "").strip().upper()
        return (d["EP"], name) if d["EP"] and name else None

    names = collections.Counter(name_key(d) for d in rows if name_key(d))
    for d in rows:
        if name_key(d) and names[name_key(d)] > 1:
            d["MOTIVO_REVISAR"] = "; ".join(
                x
                for x in [
                    d["MOTIVO_REVISAR"],
                    "el nombre %s se repite en %s, y el seccionador se identifica por su EP y su "
                    "nombre" % (d["NOMBRE"], d["EP"]),
                ]
                if x
            )
            d["REVISAR"], d["ENABLED"] = "SI", "NO"
    rows.sort(
        key=lambda d: (
            d["EP"],
            d["ESTACION"],
            d["KP_ROTULO_M"] if d["KP_ROTULO_M"] != "" else 1e9,
            d["NOMBRE"],
        )
    )
    outside_rows = [
        {
            "NOMBRE": r["name"],
            "KP_ROTULO": r["kp_txt"] or "",
            "PREFIJO": r["prefix"],
            "ESTACION_PLANO": r["plan_station"],
            "ON_LOAD": "SI" if r["on_load"] else "NO",
            "ESTADO_NORMAL": r["state"],
            "TIPO_DXF": r["type"],
            "ESTADO_DIBUJO": r["status"],
            "VIAS_DXF": ", ".join(r["tracks"]),
            "X": round(r["x"], 2),
            "Y": round(r["y"], 2),
            "HANDLE": r["handle"],
        }
        for r in outside
    ]
    outside_rows.sort(key=lambda d: (d["PREFIJO"], d["KP_ROTULO"], d["NOMBRE"]))
    return rows, outside_rows


def insulator_rows(records, master):
    """Las filas de SECTION_INSULATORS, con sus agujas, y las de AISLADORES_FUERA_MAESTRO.

    Las agujas de un escape van en las columnas AGUJA_1 ... TANGENTE_2 de su fila (la 1, la del
    extremo en VIA). El plano no rotula su KP, asi que KP_AGUJA_n sale en amarillo, y KP es la
    formula que calcula el del aislador con ellos (insulator_kp_formula). Si lo puentea un
    seccionador rotulado, KP es el suyo, escrito: manda sobre la formula hasta que se borre. Sin
    agujas, KP se escribe a mano.
    """
    rows, outside = [], []
    used_names = collections.Counter()
    # Las estaciones de todos los EP del maestro: las de las lineas que se mantienen.
    known_stations = set().union(*master["stations"].values()) if master["stations"] else set()
    for r in records:
        if future_ep(r["status"]):
            continue
        if not r.get("ep"):
            outside.append(r)
            continue
        ep, station, kind = r["ep"], r["station"], r["type"]
        plan_code = r.get("plan_station_code")
        if not station and plan_code and plan_code not in known_stations:
            # El KP lo deja en el EP, pero en el plano esta junto a una estacion que no es de ningun
            # EP del maestro: es de una linea que no se mantiene (los de NAAN, YAVNE MA'ARAV,
            # HA-RISHONIM y REHOVOT al lado de EP9B). No entra en el libro, como lo que no tiene EP.
            outside.append(r)
            continue
        tracks_of_ep = master["tracks"].get(ep, [])
        reasons = []
        if r["status"] != EN_SERVICE:
            reasons.append(
                "dibujado como %s (capa %s): entra porque esta en un EP del maestro"
                % (r["status"], r["layer"])
            )
        sides = r["tracks"]
        via, why1 = master_track(tracks_of_ep, station, sides[0] if sides else None)
        connected, why2 = (
            master_track(tracks_of_ep, station, sides[1] if len(sides) > 1 else None)
            if kind == "TRACK_CONNECTION"
            else ("", "")
        )
        codes = [s["code"] for s in r["switches"] if s]
        if kind == "TRACK_CONNECTION" and codes:
            base = "SI " + "-".join(dict.fromkeys(codes))
        elif r.get("bridged_by"):
            base = "SI " + r["bridged_by"].split(",")[0].strip()
        else:
            base = "SI V%s" % (sides[0] if sides and sides[0] else "?")
        used_names[(ep, station, base)] += 1
        name = (
            base
            if used_names[(ep, station, base)] == 1
            else "%s (%d)" % (base, used_names[(ep, station, base)])
        )
        if not station:
            reasons.append("estacion sin determinar")
        if r.get("kp_m") is None:
            reasons.append(
                "KP sin rotular en el plano: rellena el KP de las agujas y la formula de KP saca "
                "el del aislador, o escribelo en KP"
                if codes
                else "KP sin rotular en el plano: escribelo en KP"
            )
        if kind == "TRACK_CONNECTION" and len([t for t in sides if t]) < 2:
            reasons.append("falta el numero de via de algun lado del escape")
        for why in dict.fromkeys(w for w in (why1, why2) if w):
            if not (
                why.startswith("numero de via")
                and any(x.startswith("falta el numero") for x in reasons)
            ):
                reasons.append(why)
        several = ["/".join(n) for n in r["switches_near"] if len(n) > 1]
        if several:
            reasons.append("varias agujas rotuladas en el mismo cruce: %s" % ", ".join(several))
        if kind == "TRACK_CONNECTION" and len(codes) < 2:
            reasons.append("falta el codigo de aguja de algun extremo")
        if not kind:
            reasons.append("sin via debajo del simbolo")
        if r.get("station_from") == "cercania":
            reasons.append("estacion propuesta: la del plano mas cercana")
        # Las agujas, en su fila: la del extremo de cada lado del escape, sin repetir.
        switch_cells, missing = {}, set()
        seen = set()
        for k, s in enumerate(r["switches"][: len(INSULATOR_SWITCH_COLUMNS)]):
            if not s or s["code"] in seen:
                continue
            seen.add(s["code"])
            code_column, kp_column, tangent_column = INSULATOR_SWITCH_COLUMNS[k]
            switch_cells[code_column] = s["code"]
            switch_cells[tangent_column] = s["tangent"] or ""
            missing.add(kp_column)
            if not s["tangent"]:
                missing.add(tangent_column)
                reasons.append("%s: tangente sin rotular" % s["code"])
            if k < len(r["switches_near"]) and len(r["switches_near"][k]) > 1:
                reasons.append(
                    "%s: otras agujas en el mismo cruce: %s"
                    % (s["code"], "/".join(r["switches_near"][k]))
                )
        if r.get("kp_m") is not None and switch_cells:
            reasons.append(
                "KP: el del seccionador que lo puentea; borralo para que mande el de las agujas"
            )
        pole = r.get("sect_i_pole")
        complete = (
            ep
            and station
            and kind
            and via
            and (kind == "IN_TRACK" or connected)
            and r.get("kp_m") is not None
        )
        enabled = (
            "SI"
            if complete
            and not any(
                x.startswith(("varias agujas", "falta el codigo", "estacion propuesta"))
                for x in reasons
            )
            else "NO"
        )
        rows.append(
            {
                "EP": ep,
                "ESTACION": station,
                "NOMBRE": name,
                "KP": int(round(r["kp_m"])) if r.get("kp_m") is not None else "",
                "TIPO_INSTALACION": kind,
                "VIA": via,
                "VIA_CONECTADA": connected,
                **{column: switch_cells.get(column, "")
                   for switch in INSULATOR_SWITCH_COLUMNS for column in switch},
                "ENABLED": enabled,
                "REVISAR": "SI" if reasons else "NO",
                "MOTIVO_REVISAR": "; ".join(reasons),
                "KP_ORIGEN": r.get("kp_from", ""),
                "KP_ESTIMADO_M": (
                    int(round(r["kp_estimate"]))
                    if r.get("kp_m") is None and r.get("kp_estimate") is not None
                    else ""
                ),
                "VIAS_DXF": " / ".join(t or "?" for t in sides),
                "AGUJAS_DXF": " / ".join(
                    "%s 1:%s" % (s["code"], s["tangent"] or "?") if s else "?"
                    for s in r["switches"]
                ),
                "PUENTEADO_POR": r.get("bridged_by", ""),
                "POSTE_SECT_I": pole["profile"] if pole else "",
                "VIA_POSTE_SECT_I": pole["via"] if pole else "",
                "DIF_KP_POSTE_M": round(pole["kp"] - r["kp_m"], 1) if pole else "",
                "SECCION_ALIMENTACION": r["section"],
                "ESTACION_PLANO": r["plan_station"],
                "ESTADO_DIBUJO": r["status"],
                "X": round(r["x"], 2),
                "Y": round(r["y"], 2),
                "HANDLE": r["handle"],
                "_proposals": {"NOMBRE"}
                | ({"ESTACION"} if r.get("station_from") == "cercania" else set()),
                "_missing": missing,
            }
        )
    rows.sort(
        key=lambda d: (
            d["EP"],
            d["ESTACION"],
            d["KP"] if d["KP"] != "" else (d["KP_ESTIMADO_M"] or 1e9),
            d["X"],
        )
    )
    # La formula va con el numero de su fila, que solo se sabe ya ordenadas. Sin agujas no hay de
    # que calcularlo, y KP se queda vacio (en amarillo) para escribirlo a mano.
    for i, d in enumerate(rows, 2):
        if d["KP"] == "" and any(d[code] for code, _, _ in INSULATOR_SWITCH_COLUMNS):
            d["KP"] = insulator_kp_formula(i)
    outside_rows = [
        {
            "TIPO_INSTALACION": r["type"],
            "VIAS_DXF": " / ".join(t or "?" for t in r["tracks"]),
            "AGUJAS_DXF": " / ".join(
                "%s 1:%s" % (s["code"], s["tangent"] or "?") if s else "?" for s in r["switches"]
            ),
            "ESTACION_PLANO": r["plan_station"],
            "ESTADO_DIBUJO": r["status"],
            "X": round(r["x"], 2),
            "Y": round(r["y"], 2),
            "HANDLE": r["handle"],
        }
        for r in outside
    ]
    return rows, outside_rows


def insulator_kp_formula(row_number):
    """La formula de KP de un aislador en la fila row_number del libro: el KP de sus agujas.

    El punto medio de KP_AGUJA_1 y KP_AGUJA_2, redondeado al metro; si se separan mas de
    SWITCH_KP_MAX_GAP_M, el de la aguja 1; con una sola, la suya; sin ninguna, vacio.
    """
    from openpyxl.utils import get_column_letter

    kp_1 = "%s%d" % (get_column_letter(INSULATOR_COLUMNS.index("KP_AGUJA_1") + 1), row_number)
    kp_2 = "%s%d" % (get_column_letter(INSULATOR_COLUMNS.index("KP_AGUJA_2") + 1), row_number)
    return (
        '=IF(AND(ISNUMBER({a}),ISNUMBER({b})),IF(ABS({a}-{b})>{gap},{a},ROUND(({a}+{b})/2,0)),'
        'IF(ISNUMBER({a}),{a},IF(ISNUMBER({b}),{b},"")))'
    ).format(a=kp_1, b=kp_2, gap=SWITCH_KP_MAX_GAP_M)


def own_options(row):
    """Las opciones propias de una fila ("_options"), sin vacios ni repetidos."""
    own = {}
    for column, values in (row.get("_options") or {}).items():
        kept = [v for v in dict.fromkeys(values) if v not in ("", None)]
        if kept:
            own[column] = kept
    return own


def review_choices():
    """El desplegable de cada columna: f(fila) -> nombre de su lista en OPCIONES, y si es estricto.

    Lo fijo (SI/NO, el accionamiento, el tipo de instalacion, el catalogo de funciones) no admite
    otro valor. Una estacion o una via se ofrecen las del EP de la fila, pero admiten otra con un
    aviso: si lo que esta mal es el EP, la lista sigue siendo la del EP de antes. La estacion de un
    seccionador lleva ademas SIN ESTACION, al final: puede no ser de ninguna, y un aislador no.
    """

    def fixed(name):
        return lambda row: name

    def of_ep(kind):
        return lambda row: "%s %s" % (kind, row["EP"]) if row.get("EP") else None

    stations = (of_ep("ESTACIONES"), False)
    disconnector_stations = (of_ep("ESTACIONES_SECCIONADOR"), False)
    tracks = (of_ep("VIAS"), False)
    yes_no = (fixed("SI_NO"), True)
    return {
        "DISCONNECTORS": {
            "ESTACION": disconnector_stations,
            "VIA": tracks,
            "VIA_CONECTADA": tracks,
            "ON_LOAD": yes_no,
            "NORMALLY_OPEN": yes_no,
            "DRIVE_TYPE": (fixed("ACCIONAMIENTO"), True),
            "DISCONNECTOR_FUNCTION": (fixed("FUNCIONES"), True),
            "ENABLED": yes_no,
        },
        "SECTION_INSULATORS": {
            "ESTACION": stations,
            "VIA": tracks,
            "VIA_CONECTADA": tracks,
            "TIPO_INSTALACION": (fixed("TIPO_INSTALACION"), True),
            "ENABLED": yes_no,
        },
        "ESTACION_POR_PREFIJO": {"ESTACION_CORRECTA": disconnector_stations},
    }


def review_lists(master, functions):
    """Las listas de la hoja OPCIONES: las fijas, y la estacion y la via de cada EP del maestro.

    La de estaciones va dos veces: tal cual para los aisladores, y con SIN ESTACION al final para
    los seccionadores.
    """
    lists = {
        "SI_NO": ["SI", "NO"],
        "ACCIONAMIENTO": ["MOTOR", "MANUAL"],
        "TIPO_INSTALACION": ["TRACK_CONNECTION", "IN_TRACK"],
        "FUNCIONES": list(functions),
    }
    for ep in sorted(set(master["stations"]) | set(master["tracks"])):
        lists["ESTACIONES " + ep] = sorted(master["stations"].get(ep, ()))
        lists["ESTACIONES_SECCIONADOR " + ep] = lists["ESTACIONES " + ep] + [WITHOUT_STATION]
        lists["VIAS " + ep] = sorted(t["name"] for t in master["tracks"].get(ep, ()))
    return lists


# --------------------------------------------------------------------------------
# RESUMEN y PENDIENTE: lo que dicen las hojas de datos, contado
# --------------------------------------------------------------------------------
#
# Las dos hojas se calculan de las de datos y de nada mas, con una sola regla (summary_lines y
# pending_rows), tanto al generar el libro desde el plano como al refrescarlo despues de que
# alguien lo edite (--refresh). No llevan formulas: un recuento con formula cuenta lo que cuenta la
# formula (los HANDLE, y no las filas añadidas a mano; una hoja que ya no se usa), y guarda el valor
# de la ultima vez que alguien lo abrio en Excel. check_review dice si lo escrito sigue casando.

SUMMARY_SHEET, PENDING_SHEET = "RESUMEN", "PENDIENTE"
SUMMARY_COLUMNS = ["Concepto", "Cantidad"]
PENDING_COLUMNS = [
    "SECCION",
    "HOJA",
    "FILA",
    "EP",
    "ESTACION",
    "VIA",
    "NOMBRE",
    "ENABLED",
    "REVISAR",
    "COMPLETAR",
    "MOTIVO_REVISAR",
]
PENDING_HEADER_ROW = 4  # titulo, nota, leyenda y cabecera; los datos, desde la fila 5
PENDING_TITLE = "Lo que falta por revisar del seccionamiento"
PENDING_NOTE = (
    "Calculada de DISCONNECTORS y SECTION_INSULATORS por build_sectioning_review.py: no se edita, y "
    "despues de cambiar el libro se refresca con --refresh (data/README.md). Una fila esta "
    "revisada cuando su REVISAR dice NO. Aqui salen las demas: las de REVISAR = SI, con su motivo, "
    "y las de REVISAR vacio. Revisar una fila es mirarla contra el plano segun MOTIVO_REVISAR, "
    "corregir lo que haga falta, decidir ENABLED y poner REVISAR = NO. COMPLETAR dice el dato que "
    "falta en la fila."
)
PENDING_LEGEND = "FILA lleva a la fila en su hoja."
PENDING_MISSING_LEGEND = "COMPLETAR: lo que falta"
PENDING_UNMARKED = "REVISAR vacio: fila añadida a mano o sin marcar"
PENDING_SECTIONS = {
    ("DISCONNECTORS", True): "1. Seccionadores que ya se cargan (ENABLED = SI) sin revisar",
    ("DISCONNECTORS", False): "2. Seccionadores sin revisar que no se cargan",
    ("SECTION_INSULATORS", True): "3. Aisladores que ya se cargan (ENABLED = SI) sin revisar",
    ("SECTION_INSULATORS", False): "4. Aisladores sin revisar que no se cargan",
    None: "5. Revisadas que se cargan y a las que les falta un dato",
}
# El dato que le falta a una fila, y que se ve en la propia fila sin abrir el maestro. No es todo
# lo que comprueba el importador (que el poste exista, que la via sea del EP...): eso lo dice su
# informe.
REQUIRED_DATA = {
    "DISCONNECTORS": ("EP", "ESTACION", "NOMBRE", "ON_LOAD", "DISCONNECTOR_FUNCTION"),
    "SECTION_INSULATORS": ("EP", "ESTACION", "NOMBRE", "VIA"),
}


def filled(value):
    return value is not None and str(value).strip() != ""


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def insulator_has_kp(row):
    """El aislador tiene KP: uno escrito, o el de alguna de sus agujas (la formula de KP)."""
    if is_number(row.get("KP")):
        return True
    return any(is_number(row.get(kp)) for _, kp, _ in INSULATOR_SWITCH_COLUMNS)


def missing_data(sheet, row):
    """Las columnas que le faltan a la fila (COMPLETAR), en el orden de la hoja."""
    missing = [column for column in REQUIRED_DATA.get(sheet, ()) if not filled(row.get(column))]
    if sheet == "DISCONNECTORS":
        # Uno de puesta en paralelo (Disc/PP, LoadB/PP y sus -pr) une su via con otra: sin ella
        # no dice cual. FS/PP es una alimentacion, no un seccionador entre dos vias.
        function = str(row.get("DISCONNECTOR_FUNCTION") or "")
        parallel = function.startswith(DISCONNECTOR_CODE_PREFIXES) and PARALLEL_FUNCTION in function
        if parallel and not filled(row.get("VIA_CONECTADA")):
            missing.append("VIA_CONECTADA")
    if sheet == "SECTION_INSULATORS":
        if not insulator_has_kp(row):
            missing.append("KP")
        for code, kp, tangent in INSULATOR_SWITCH_COLUMNS:
            # Una aguja con KP o tangente y sin codigo no se carga: va a DESCARTADOS.
            if not filled(row.get(code)) and (filled(row.get(kp)) or filled(row.get(tangent))):
                missing.append(code)
    return missing


def pending_rows(data):
    """Las filas de la hoja PENDIENTE: las no revisadas, y las revisadas que se cargan sin un dato.

    data es {hoja: [fila]}, cada fila con sus columnas y su numero de fila en "_row".
    """
    found = []
    for sheet in REVIEW_SHEETS:
        for row in data.get(sheet, ()):
            state = review_state(row)
            loads = flag(row.get("ENABLED")) == "SI"
            missing = missing_data(sheet, row)
            if state == REVIEWED and not (loads and missing):
                continue
            reason = row.get("MOTIVO_REVISAR")
            if state == UNMARKED and not filled(reason):
                reason = PENDING_UNMARKED
            found.append(
                {
                    "SECCION": PENDING_SECTIONS[None if state == REVIEWED else (sheet, loads)],
                    "HOJA": sheet,
                    "FILA": row["_row"],
                    "EP": row.get("EP"),
                    "ESTACION": row.get("ESTACION"),
                    "VIA": row.get("VIA"),
                    "NOMBRE": row.get("NOMBRE"),
                    "ENABLED": row.get("ENABLED"),
                    "REVISAR": row.get("REVISAR"),
                    "COMPLETAR": ", ".join(missing),
                    "MOTIVO_REVISAR": reason,
                }
            )
    found.sort(key=lambda r: (r["SECCION"], REVIEW_SHEETS.index(r["HOJA"]), r["FILA"]))
    return found


def summary_lines(data):
    """Las lineas de la hoja RESUMEN, (concepto, cantidad), contadas de las hojas de datos.

    Un concepto sangrado es parte del anterior con menos sangria.
    """

    pending = pending_rows(data)

    def rows(sheet):
        return data.get(sheet, ())

    def count(sheet, test):
        return sum(1 for r in rows(sheet) if test(r))

    def reviewing(sheet, indent):
        loads = lambda r: flag(r.get("ENABLED")) == "SI"  # noqa: E731
        return [
            (indent + "se cargan (ENABLED = SI)", count(sheet, loads)),
            (indent + "revisados (REVISAR = NO)", count(sheet, is_reviewed)),
            (
                indent + "sin revisar",
                count(sheet, lambda r: not is_reviewed(r)),
            ),
            (
                indent + "  con REVISAR = SI",
                count(sheet, lambda r: review_state(r) == TO_REVIEW),
            ),
            (
                indent + "  con REVISAR vacio (sin marcar)",
                count(sheet, lambda r: review_state(r) == UNMARKED),
            ),
            (
                indent + "  y ya se cargan (ENABLED = SI)",
                count(sheet, lambda r: not is_reviewed(r) and loads(r)),
            ),
            (
                indent + "en la hoja PENDIENTE con un dato que falta (COMPLETAR)",
                sum(1 for r in pending if r["HOJA"] == sheet and r["COMPLETAR"]),
            ),
        ]

    in_service = lambda r: r.get("ESTADO_DIBUJO") == EN_SERVICE  # noqa: E731
    switches = [
        (r.get(code), r.get(kp), r.get(tangent))
        for r in rows("SECTION_INSULATORS")
        for code, kp, tangent in INSULATOR_SWITCH_COLUMNS
        if filled(r.get(code))
    ]
    return (
        [
            ("Seccionadores dibujados (todos)", len(rows("SECCIONADORES_DXF"))),
            ("  en servicio", count("SECCIONADORES_DXF", in_service)),
            (
                "  futuros, deshabilitados, en via no electrificada o del deposito de Haifa",
                count("SECCIONADORES_DXF", lambda r: not in_service(r)),
            ),
            ("En los EP del maestro (hoja DISCONNECTORS)", len(rows("DISCONNECTORS"))),
            (
                "  dibujados como futuros, deshabilitados o en via no electrificada",
                count("DISCONNECTORS", lambda r: not in_service(r)),
            ),
            (
                "  con poste (PROFILE_ID)",
                count("DISCONNECTORS", lambda r: filled(r.get("PROFILE_ID"))),
            ),
            ("  sin poste", count("DISCONNECTORS", lambda r: not filled(r.get("PROFILE_ID")))),
            (
                "  que ponen dos vias en paralelo con su VIA_CONECTADA",
                count("DISCONNECTORS", lambda r: filled(r.get("VIA_CONECTADA"))),
            ),
        ]
        + reviewing("DISCONNECTORS", "  ")
        + [
            (
                "Prefijos con estacion propuesta (hoja ESTACION_POR_PREFIJO)",
                len(rows("ESTACION_POR_PREFIJO")),
            ),
            (
                "  con ESTACION_CORRECTA escrita",
                count("ESTACION_POR_PREFIJO", lambda r: filled(r.get("ESTACION_CORRECTA"))),
            ),
            (
                "Seccionadores en lineas que el maestro no tiene (hoja SECCIONADORES_FUERA_MAESTRO)",
                len(rows("SECCIONADORES_FUERA_MAESTRO")),
            ),
            ("Aisladores dibujados (todos)", len(rows("AISLADORES_DXF"))),
            ("En los EP del maestro (hoja SECTION_INSULATORS)", len(rows("SECTION_INSULATORS"))),
            (
                "  salidos del plano (con HANDLE)",
                count("SECTION_INSULATORS", lambda r: filled(r.get("HANDLE"))),
            ),
            (
                "  añadidos a mano (sin HANDLE)",
                count("SECTION_INSULATORS", lambda r: not filled(r.get("HANDLE"))),
            ),
            ("  con KP (escrito o de sus agujas)", count("SECTION_INSULATORS", insulator_has_kp)),
        ]
        + reviewing("SECTION_INSULATORS", "  ")
        + [
            (
                "Aisladores en lineas que el maestro no tiene (hoja AISLADORES_FUERA_MAESTRO)",
                len(rows("AISLADORES_FUERA_MAESTRO")),
            ),
            ("Agujas (en la fila de su aislador)", len(switches)),
            ("  con KP", sum(1 for _, kp, _ in switches if is_number(kp))),
            ("  con tangente", sum(1 for _, _, tangent in switches if is_number(tangent))),
            ("Rotulos de seccionador sin simbolo", len(rows("ROTULOS_SIN_SIMBOLO"))),
            ("Postes del maestro sin casar", len(rows("POSTES_SIN_CASAR"))),
            ("Filas en la hoja PENDIENTE", len(pending)),
        ]
    )


def read_review_data(path):
    """Las hojas de datos del libro, {hoja: [fila]}, cada fila con su numero en "_row".

    Lee lo escrito, no lo calculado: una formula llega como su texto (el KP de un aislador), que es
    lo que distingue un numero puesto a mano del que sale de las agujas.
    """
    import openpyxl
    import warnings

    with warnings.catch_warnings():
        # Los desplegables que guarda Excel van en una extension que openpyxl no lee, y lo avisa
        # en cada hoja. Aqui solo se lee; quien escribe (refresh_review) no pasa por openpyxl, y
        # por eso no los pierde.
        warnings.simplefilter("ignore")
        wb = openpyxl.load_workbook(path, read_only=True, data_only=False)
        try:
            data = {}
            for ws in wb.worksheets:
                if ws.title in (SUMMARY_SHEET, PENDING_SHEET, "LEEME"):
                    continue
                values = ws.iter_rows(values_only=True)
                header = [str(h).strip() if h is not None else "" for h in next(values, ())]
                data[ws.title] = [
                    dict(zip(header, row), _row=index)
                    for index, row in enumerate(values, start=2)
                    if any(filled(v) for v in row)
                ]
            return data
        finally:
            wb.close()


def _sheet_values(path, sheet, first_row, width):
    import openpyxl
    import warnings

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        try:
            if sheet not in wb.sheetnames:
                return None
            found = []
            for row in wb[sheet].iter_rows(min_row=first_row, values_only=True):
                row = tuple(row[:width]) + (None,) * (width - len(row))
                if any(filled(v) for v in row):
                    found.append(tuple(None if not filled(v) else v for v in row))
            return found
        finally:
            wb.close()


def check_review(path):
    """Lo que RESUMEN y PENDIENTE dicen y las hojas de datos no: una lista de diferencias.

    Vacia si casan. Es lo que comprueba el test del libro versionado (data/sectioning-review.xlsx)
    y --check: un libro editado y sin refrescar no pasa.
    """
    data = read_review_data(path)
    problems = []

    def compare(sheet, written, expected):
        if written is None:
            problems.append("falta la hoja %s" % sheet)
            return
        if len(written) != len(expected):
            problems.append(
                "%s: %d filas escritas y %d calculadas" % (sheet, len(written), len(expected))
            )
        for index, (a, b) in enumerate(zip(written, expected)):
            if a != b:
                problems.append("%s, linea %d: dice %r y los datos dan %r" % (sheet, index + 1, a, b))

    clean = lambda row: tuple(None if not filled(v) else v for v in row)  # noqa: E731
    compare(SUMMARY_SHEET, _sheet_values(path, SUMMARY_SHEET, 2, 2), summary_lines(data))
    compare(
        PENDING_SHEET,
        _sheet_values(path, PENDING_SHEET, PENDING_HEADER_ROW + 1, len(PENDING_COLUMNS)),
        [clean(row[c] for c in PENDING_COLUMNS) for row in pending_rows(data)],
    )
    return problems


def _xml_cell(ref, value, style=None):
    from xml.sax.saxutils import escape

    s = "" if style is None else ' s="%s"' % style
    if not filled(value):
        return "" if style is None else '<c r="%s"%s/>' % (ref, s)
    if is_number(value):
        return '<c r="%s"%s><v>%r</v></c>' % (ref, s, value)
    return '<c r="%s"%s t="inlineStr"><is><t xml:space="preserve">%s</t></is></c>' % (
        ref,
        s,
        escape(str(value)),
    )


_SHEET_NS = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
)


def _summary_xml(lines, styles):
    """La hoja RESUMEN. styles: el estilo de la cabecera, de un concepto, de una parte y del numero."""
    out = [
        _SHEET_NS,
        '<sheetViews><sheetView workbookViewId="0"/></sheetViews>',
        '<cols><col min="1" max="1" width="84" customWidth="1"/>'
        '<col min="2" max="2" width="12" customWidth="1"/></cols><sheetData>',
        '<row r="1">%s%s</row>'
        % tuple(
            _xml_cell("%s1" % letter, h, styles.get("A1")) for letter, h in zip("AB", SUMMARY_COLUMNS)
        ),
    ]
    for i, (label, value) in enumerate(lines, 2):
        style = styles.get("A3") if label.startswith(" ") else styles.get("A2")
        out.append(
            '<row r="%d">%s%s</row>'
            % (i, _xml_cell("A%d" % i, label, style), _xml_cell("B%d" % i, value, styles.get("B2")))
        )
    out.append("</sheetData></worksheet>")
    return "".join(out)


def _pending_xml(rows, styles):
    """La hoja PENDIENTE: titulo, nota, leyenda, cabecera con filtro y una fila por pendiente."""
    from openpyxl.utils import get_column_letter
    from xml.sax.saxutils import quoteattr

    letters = [get_column_letter(j) for j in range(1, len(PENDING_COLUMNS) + 1)]
    last, head = letters[-1], PENDING_HEADER_ROW
    link, missing = letters[PENDING_COLUMNS.index("FILA")], letters[PENDING_COLUMNS.index("COMPLETAR")]
    widths = dict(zip(PENDING_COLUMNS, (58, 22, 7, 8, 16, 30, 24, 9, 9, 30, 110)))
    out = [
        _SHEET_NS,
        '<sheetViews><sheetView workbookViewId="0"><pane ySplit="%d" topLeftCell="A%d" '
        'activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>' % (head, head + 1),
        "".join(
            '<col min="%d" max="%d" width="%d" customWidth="1"/>' % (j, j, widths[c])
            for j, c in enumerate(PENDING_COLUMNS, 1)
        ),
        "</cols><sheetData>",
        '<row r="1">%s</row>' % _xml_cell("A1", PENDING_TITLE, styles.get("A1")),
        '<row r="2" ht="64" customHeight="1">%s</row>' % _xml_cell("A2", PENDING_NOTE, styles.get("A2")),
        '<row r="3">%s%s</row>'
        % (
            _xml_cell("%s3" % link, PENDING_LEGEND, styles.get("link")),
            _xml_cell("%s3" % missing, PENDING_MISSING_LEGEND, styles.get("missing")),
        ),
        '<row r="%d">%s</row>'
        % (
            head,
            "".join(
                _xml_cell("%s%d" % (letter, head), c, styles.get("A4"))
                for letter, c in zip(letters, PENDING_COLUMNS)
            ),
        ),
    ]
    links = []
    for i, row in enumerate(rows, head + 1):
        cells = []
        for letter, column in zip(letters, PENDING_COLUMNS):
            style = None
            if column == "FILA":
                style = styles.get("link")
                links.append(
                    '<hyperlink ref="%s%d" location=%s display="%d"/>'
                    % (letter, i, quoteattr("'%s'!A%d" % (row["HOJA"], row["FILA"])), row["FILA"])
                )
            elif column == "COMPLETAR" and filled(row[column]):
                style = styles.get("missing")
            cells.append(_xml_cell("%s%d" % (letter, i), row[column], style))
        out.append('<row r="%d">%s</row>' % (i, "".join(cells)))
    out.append("</sheetData>")
    out.append('<autoFilter ref="A%d:%s%d"/>' % (head, last, head + len(rows)))
    out.append('<mergeCells count="1"><mergeCell ref="A2:%s2"/></mergeCells>' % last)
    if links:
        out.append("<hyperlinks>%s</hyperlinks>" % "".join(links))
    out.append("</worksheet>")
    return "".join(out)


def refresh_review(path):
    """Vuelve a escribir RESUMEN y PENDIENTE con lo que dicen las hojas de datos del libro.

    Solo esas dos hojas. El resto del libro no pasa por openpyxl, que al guardar pierde lo que no
    entiende (los desplegables que escribe Excel van en una extension): se copia tal cual, parte a
    parte del .xlsx, y lo unico que cambia fuera de las dos hojas es el rango del filtro de
    PENDIENTE en el libro. Lo que alguien ha rellenado en las hojas de datos no se toca.
    """
    import shutil
    import tempfile
    import xml.etree.ElementTree as ET
    import zipfile

    from openpyxl.utils import get_column_letter

    data = read_review_data(path)
    lines, pending = summary_lines(data), pending_rows(data)

    rel = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
    main = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    package = "{http://schemas.openxmlformats.org/package/2006/relationships}"
    with zipfile.ZipFile(path) as source:
        infos = source.infolist()
        parts = {info.filename: source.read(info.filename) for info in infos}
    targets = {
        r.get("Id"): r.get("Target")
        for r in ET.fromstring(parts["xl/_rels/workbook.xml.rels"]).iter(package + "Relationship")
    }
    sheets = {}
    for sheet in ET.fromstring(parts["xl/workbook.xml"]).iter(main + "sheet"):
        target = targets[sheet.get(rel)]
        sheets[sheet.get("name")] = target.lstrip("/") if target.startswith("/") else "xl/" + target
    for name in (SUMMARY_SHEET, PENDING_SHEET):
        if name not in sheets:
            raise SystemExit(
                "%s no tiene la hoja %s: no es un libro de revision de este generador, o es de "
                "antes de que la llevara. Se regenera desde el plano." % (path, name)
            )
        folder, _, filename = sheets[name].rpartition("/")
        if "%s/_rels/%s.rels" % (folder, filename) in parts:
            raise SystemExit(
                "la hoja %s de %s lleva comentarios o dibujos, y es una hoja calculada: quitalos "
                "antes de refrescarla." % (name, path)
            )

    def cell_styles(part):
        return dict(re.findall(r'<c r="([A-Z]+\d+)"[^>]*?\bs="(\d+)"', parts[part].decode("utf-8")))

    written = cell_styles(sheets[SUMMARY_SHEET])
    parts[sheets[SUMMARY_SHEET]] = _summary_xml(lines, written).encode("utf-8")
    written = cell_styles(sheets[PENDING_SHEET])
    # La leyenda de la fila 3 guarda el estilo del enlace y el del dato que falta, para que
    # sobrevivan a un libro sin nada pendiente. Un libro de antes los llevaba en su primera fila.
    written["link"] = written.get("C3") or written.get("C5")
    written["missing"] = written.get("J3") or written.get("I5")
    parts[sheets[PENDING_SHEET]] = _pending_xml(pending, written).encode("utf-8")
    # El filtro de una hoja es tambien un nombre oculto del libro, con su rango.
    workbook = parts["xl/workbook.xml"].decode("utf-8")
    workbook = re.sub(
        r'(<definedName\b[^>]*name="_xlnm\._FilterDatabase"[^>]*>)\'?%s\'?![^<]*(</definedName>)'
        % PENDING_SHEET,
        lambda m: "%s%s!$A$%d:$%s$%d%s"
        % (
            m.group(1),
            PENDING_SHEET,
            PENDING_HEADER_ROW,
            get_column_letter(len(PENDING_COLUMNS)),
            PENDING_HEADER_ROW + len(pending),
            m.group(2),
        ),
        workbook,
    )
    parts["xl/workbook.xml"] = workbook.encode("utf-8")

    handle, temporary = tempfile.mkstemp(suffix=".xlsx", dir=os.path.dirname(os.path.abspath(path)))
    os.close(handle)
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as target:
            for info in infos:
                target.writestr(info, parts[info.filename])
        shutil.copymode(path, temporary)
        shutil.move(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.remove(temporary)
    return lines, pending


def write_review(path, sheets_data, readme, choices=None):
    """El libro de revision.

    choices son los desplegables: {"lists": {nombre: [valores]}, "columns": {hoja: {columna:
    (f(fila) -> nombre de la lista, estricto)}}} (review_lists y review_choices). Las listas van a
    la hoja OPCIONES. Una fila puede traer ademas sus propias opciones en "_options" (el poste, el
    nombre), que van a una fila de la hoja oculta OPCIONES_FILA y admiten otro valor con un aviso.
    No van dentro de la validacion de la celda: ahi todo es texto, y Excel no admite mas de 255
    caracteres ni una coma dentro de un valor.
    """
    import openpyxl
    from openpyxl.comments import Comment
    from openpyxl.styles import Alignment, Font, PatternFill
    from openpyxl.utils import get_column_letter
    from openpyxl.worksheet.datavalidation import DataValidation

    choices = choices or {}
    lists = {name: list(values) for name, values in choices.get("lists", {}).items() if values}
    per_row = []  # (hoja, fila, columna, valores): una fila de OPCIONES_FILA cada una
    ranges = {}
    for j, (name, values) in enumerate(lists.items(), 1):
        letter = get_column_letter(j)
        ranges[name] = "OPCIONES!$%s$2:$%s$%d" % (letter, letter, len(values) + 1)

    def list_validation(formula, strict):
        dv = DataValidation(
            type="list",
            formula1=formula,
            allow_blank=True,
            showErrorMessage=True,
            errorStyle="stop" if strict else "warning",
        )
        dv.errorTitle = "Fuera de la lista"
        dv.error = (
            "Elige uno de la lista (hoja OPCIONES)."
            if strict
            else "No es una de las opciones. Si sabes que es correcto, acepta y se queda."
        )
        return dv

    arial = "Arial"
    header_font = Font(name=arial, bold=True, color="FFFFFF", size=10)
    master_fill, help_fill = PatternFill("solid", fgColor="1F3864"), PatternFill(
        "solid", fgColor="7F7F7F"
    )
    body = Font(name=arial, size=10)
    missing = PatternFill("solid", fgColor="FFFF00")
    proposal = PatternFill("solid", fgColor="FFC000")
    review = PatternFill("solid", fgColor="F8CBAD")
    ready = PatternFill("solid", fgColor="C6EFCE")

    wb = openpyxl.Workbook()
    wb.remove(wb.active)
    info = wb.create_sheet("LEEME")
    pending = wb.create_sheet(PENDING_SHEET)
    summary = wb.create_sheet(SUMMARY_SHEET)
    for title, columns, master_columns, required, data, comments in sheets_data:
        ws = wb.create_sheet(title)
        for j, h in enumerate(columns, 1):
            c = ws.cell(row=1, column=j, value=h)
            c.font = header_font
            c.fill = master_fill if (master_columns == 0 or j <= master_columns) else help_fill
            c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
            if comments and h in comments:
                c.comment = Comment(comments[h], "build_sectioning_review.py")
        for i, row in enumerate(data, 2):
            proposals = row.get("_proposals", set())
            # Lo que falta en esta fila y no en todas: la via conectada, solo en las que la piden.
            row_required = required | row.get("_missing", set())
            for j, h in enumerate(columns, 1):
                v = row.get(h, "")
                c = ws.cell(row=i, column=j, value="" if v is None else v)
                c.font = body
                if h in proposals and v not in ("", None):
                    c.fill = proposal
                elif h in row_required and v in ("", None):
                    c.fill = missing
                elif h == "REVISAR" and v == "SI":
                    c.fill = review
                elif h == "ENABLED" and v == "SI":
                    c.fill = ready
        # Los desplegables: los de la fila (el poste, el nombre), de la hoja oculta OPCIONES_FILA;
        # los de la columna, de la hoja OPCIONES. Una celda solo admite uno, y gana el de la fila.
        shared = {}
        for column, (chooser, strict) in choices.get("columns", {}).get(title, {}).items():
            if column not in columns:
                continue
            j = columns.index(column) + 1
            for i, row in enumerate(data, 2):
                name = chooser(row)
                if name not in ranges or column in own_options(row):
                    continue
                if (column, name) not in shared:
                    shared[(column, name)] = list_validation(ranges[name], strict)
                    ws.add_data_validation(shared[(column, name)])
                shared[(column, name)].add(ws.cell(row=i, column=j))
        for i, row in enumerate(data, 2):
            for column, values in own_options(row).items():
                if column not in columns:
                    continue
                k = len(per_row) + 2
                per_row.append((title, i, column, values))
                last = get_column_letter(3 + len(values))
                dv = list_validation("OPCIONES_FILA!$D$%d:$%s$%d" % (k, last, k), strict=False)
                ws.add_data_validation(dv)
                dv.add(ws.cell(row=i, column=columns.index(column) + 1))
        ws.freeze_panes = "A2"
        ws.auto_filter.ref = "A1:%s%d" % (get_column_letter(len(columns)), max(1, len(data) + 1))
        for j, h in enumerate(columns, 1):
            width = max(
                [len(h)]
                + [
                    len(str(ws.cell(row=i, column=j).value or ""))
                    for i in range(2, min(len(data) + 2, 400))
                    # Una formula no se ve: su ancho no es el de la columna.
                    if not str(ws.cell(row=i, column=j).value or "").startswith("=")
                ]
            )
            ws.column_dimensions[get_column_letter(j)].width = min(60, max(8, width + 2))
        ws.row_dimensions[1].height = 30

    if lists:
        options = wb.create_sheet("OPCIONES")
        for j, (name, values) in enumerate(lists.items(), 1):
            c = options.cell(row=1, column=j, value=name)
            c.font, c.fill = header_font, master_fill
            for i, value in enumerate(values, 2):
                options.cell(row=i, column=j, value=value).font = body
            width = max(len(str(v)) for v in [name] + values)
            options.column_dimensions[get_column_letter(j)].width = min(40, max(10, width + 2))
        options.freeze_panes = "A2"
    if per_row:
        # Cada valor con su tipo: un KP es un numero, y asi se elige como el resto de la columna.
        own = wb.create_sheet("OPCIONES_FILA")
        width = max(len(values) for *_, values in per_row)
        own.append(["HOJA", "FILA", "COLUMNA"] + ["OPCION_%d" % n for n in range(1, width + 1)])
        for title, i, column, values in per_row:
            own.append([title, i, column] + list(values))
        own.sheet_state = "hidden"

    # RESUMEN y PENDIENTE se calculan de las hojas de datos ya escritas (refresh_review, al final),
    # con la misma regla que cuando alguien edita el libro y lo refresca. Aqui solo van sus
    # estilos, en las celdas de donde refresh_review los toma.
    for j, h in enumerate(SUMMARY_COLUMNS, 1):
        c = summary.cell(row=1, column=j, value=h)
        c.font, c.fill = header_font, master_fill
    summary.cell(row=2, column=1, value="-").font = Font(name=arial, size=10, bold=True)
    summary.cell(row=2, column=2, value=0).font = body
    summary.cell(row=3, column=1, value=" -").font = body
    pending.cell(row=1, column=1, value=PENDING_TITLE).font = Font(name=arial, size=14, bold=True)
    note = pending.cell(row=2, column=1, value=PENDING_NOTE)
    note.font, note.alignment = body, Alignment(wrap_text=True, vertical="top")
    legend = pending.cell(row=3, column=PENDING_COLUMNS.index("FILA") + 1, value=PENDING_LEGEND)
    legend.font = Font(name=arial, size=10, color="0563C1", underline="single")
    legend = pending.cell(
        row=3, column=PENDING_COLUMNS.index("COMPLETAR") + 1, value=PENDING_MISSING_LEGEND
    )
    legend.font, legend.fill = body, missing
    for j, h in enumerate(PENDING_COLUMNS, 1):
        c = pending.cell(row=PENDING_HEADER_ROW, column=j, value=h)
        c.font, c.fill = header_font, master_fill
    pending.auto_filter.ref = "A%d:%s%d" % (
        PENDING_HEADER_ROW,
        get_column_letter(len(PENDING_COLUMNS)),
        PENDING_HEADER_ROW,
    )

    info.column_dimensions["A"].width = 30
    info.column_dimensions["B"].width = 130
    info.cell(row=1, column=1, value="Seccionamiento desde el DXF: libro de revision").font = Font(
        name=arial, size=14, bold=True
    )
    row = 3
    for key, text in readme:
        a = info.cell(row=row, column=1, value=key)
        a.font, a.alignment = Font(name=arial, size=10, bold=True), Alignment(vertical="top")
        b = info.cell(row=row, column=2, value=text)
        b.font, b.alignment = body, Alignment(wrap_text=True, vertical="top")
        row += 1
    row += 1
    info.cell(row=row, column=1, value="COLORES").font = Font(name=arial, size=10, bold=True)
    for fill, text in (
        (missing, "dato que falta"),
        (proposal, "propuesta del generador: confirmar o corregir"),
        (review, "fila a revisar (motivo en MOTIVO_REVISAR)"),
        (ready, "fila lista para cargar"),
    ):
        row += 1
        info.cell(row=row, column=1).fill = fill
        info.cell(row=row, column=2, value=text).font = body
    wb.save(path)
    refresh_review(path)


README = [
    (
        "QUE ES",
        "Seccionadores y aisladores de seccion del plano de operacion (DXF), cruzados con "
        "data/profile-master.xlsx. Lo genera data/tools/build_sectioning_review.py; "
        "data/README.md explica como.",
    ),
    (
        "HOJAS DEL MAESTRO",
        "DISCONNECTORS y SECTION_INSULATORS llevan primero, con cabecera azul, las columnas que "
        "build_profile_master.py copia a profile-master.xlsx; las agujas de cada aislador van en "
        "su fila, y de ellas sale la hoja SECTION_INSULATOR_SWITCHES del maestro. Las de "
        "cabecera gris son para revisar.",
    ),
    (
        "QUE ENTRA",
        "Todo lo que esta en un EP del maestro, sea cual sea su capa: en servicio, futuro, en via "
        "no electrificada o la zona neutra deshabilitada de Holtz. ESTADO_DIBUJO dice cual, y lo "
        "que no esta en servicio lleva una nota en MOTIVO_REVISAR. Lo de lineas que el maestro no "
        "tiene va a SECCIONADORES_FUERA_MAESTRO y AISLADORES_FUERA_MAESTRO; el deposito de "
        "Haifa/Kishon, de EP futuros, solo sale en SECCIONADORES_DXF y AISLADORES_DXF. El color "
        "del rotulo (instalado o no) se ignora: esta desactualizado.",
    ),
    (
        "QUE EDITAR",
        "Amarillo = falta; naranja = propuesta del generador, a confirmar. Revisa cada fila con "
        "REVISAR = SI segun MOTIVO_REVISAR, corrige lo necesario, pon ENABLED = SI si se carga y "
        "REVISAR = NO cuando la des por revisada. Solo las filas con ENABLED = SI se cargarian.",
    ),
    (
        "FILA REVISADA",
        "Una fila de DISCONNECTORS o de SECTION_INSULATORS esta revisada cuando su REVISAR dice "
        "NO, y solo entonces. Con SI esta pendiente, y vacia tambien (una fila añadida a mano se "
        "marca igual). ENABLED no lo dice: decide si la fila se carga, y una fila puede cargarse "
        "con una duda abierta. MOTIVO_REVISAR se deja como esta: es por que se pregunto.",
    ),
    (
        "NOMBRE Y KP",
        "Del rotulo junto al simbolo ('TSA-BF06 / KP93+451'). KP en metros, como en el maestro: "
        "KP93+451 = 93451.",
    ),
    (
        "ON_LOAD",
        "Del simbolo: circulo de accionamiento medio relleno = on-load, vacio = off-load.",
    ),
    (
        "NORMALLY_OPEN",
        "Estado normal, de la cuchilla dibujada: inclinada = abierta = SI; alineada con sus "
        "contactos = cerrada = NO.",
    ),
    (
        "DRIVE_TYPE",
        "MOTOR si el simbolo lleva el circulo del accionamiento (todos los del plano lo llevan); "
        "MANUAL si no tiene motor. Vacio es valido: el dominio no lo exige.",
    ),
    (
        "PROFILE_ID",
        "Poste del maestro con seccionador en SECTIONING_FEEDING, mismo EP, a menos de 80 m del "
        "KP, preferente en la misma via y del mismo tipo. En naranja: el poste mas cercano (a "
        "menos de 30 m) cuando ninguno lleva el codigo, con su VIA y su KP_POSTE. Es opcional: "
        "un seccionador que no esta en un poste lo lleva vacio, y entonces VIA no cuenta.",
    ),
    (
        "KP_POSTE",
        "El KP del poste de PROFILE_ID, en metros, copiado del maestro. Solo lo mira el "
        "importador cuando la via repite ese PROFILE_ID, que pasa en las vias de dos tramos "
        "concatenados (EP9A): entonces es lo unico que distingue un poste del otro. Si cambias "
        "de poste en una de esas vias, cambia tambien KP_POSTE; si no, el importador lo dice.",
    ),
    (
        "VIA_CONECTADA",
        "La otra via de un seccionador que pone dos en paralelo (Disc/PP, LoadB/PP; casi siempre "
        "la B o BF del by-pass en el nombre), con poste o sin el. Su VIA es la de su poste, o la "
        "suya sin poste, y esta es con la que la une, nunca la misma. En naranja, la que dice el "
        "plano (las patas del simbolo tocan dos vias). En amarillo, una de puesta en paralelo de "
        "la que el plano no dice cual: escribela tu. Vacia en los demas.",
    ),
    (
        "KP Y VIA SIN POSTE",
        "Un seccionador sin poste guarda su propio KP (en metros) y su VIA; uno en un poste, no: "
        "son los de su perfil, y KP va vacio. Si quitas el poste de una fila, copia KP_ROTULO_M "
        "en KP y comprueba la VIA; si se lo pones, vacia KP.",
    ),
    (
        "DESPLEGABLES",
        "Las columnas que se eligen llevan desplegable. ESTACION, VIA y VIA_CONECTADA, las del EP "
        "de la fila (y ESTACION_CORRECTA, en ESTACION_POR_PREFIJO), con SIN ESTACION al final en "
        "la estacion de un seccionador; DISCONNECTOR_FUNCTION, el "
        "catalogo, los de seccionador primero; ON_LOAD, NORMALLY_OPEN y ENABLED, SI o NO; "
        "DRIVE_TYPE, MOTOR o MANUAL; TIPO_INSTALACION, TRACK_CONNECTION o IN_TRACK. PROFILE_ID y "
        "KP_POSTE, el poste de la fila primero y despues los del maestro a menos de 80 m del KP "
        "del rotulo, del mas cercano al mas lejano; POSTES_CERCANOS dice de cada uno su via, su "
        "KP, a cuantos metros esta y sus codigos: si cambias de poste, cambia tambien VIA y "
        "KP_POSTE. NOMBRE, en un simbolo sin rotulo, los rotulos sueltos de al lado. Las listas "
        "estan en la hoja OPCIONES, y las de cada fila, en la hoja oculta OPCIONES_FILA. En "
        "estacion, via, poste y nombre, un valor que no esta en la lista se admite con un aviso; "
        "en lo demas, no. KP, TANGENTE y el nombre de un aislador no llevan: son un numero o un "
        "nombre que no sale de ninguna lista.",
    ),
    (
        "DESPUES DE REVISAR",
        "Guarda el libro como data/sectioning-review.xlsx: build_profile_master.py copia "
        "DISCONNECTORS y SECTION_INSULATORS (solo las columnas de cabecera azul) a "
        "profile-master.xlsx, escribe SECTION_INSULATOR_SWITCHES con las agujas de cada aislador "
        "y aplica ESTACION_CORRECTA a los seccionadores de su prefijo. Solo se carga lo que "
        "tenga ENABLED = SI.",
    ),
    (
        "RESUMEN Y PENDIENTE",
        "Se calculan de las hojas de datos y no se editan: RESUMEN cuenta y PENDIENTE lista lo que "
        "no esta revisado. No llevan formulas, asi que no se recalculan solas: despues de editar "
        "el libro, build_sectioning_review.py --refresh data/sectioning-review.xlsx las vuelve a "
        "escribir sin tocar ninguna otra hoja, y --check dice si casan.",
    ),
    (
        "SIN POSTE",
        "Los de alimentacion (FP en el nombre) van en el portico de la subestacion, y los de "
        "puesta a tierra solo van en un poste que el maestro marca con ED: sin el, salen sin "
        "poste y con una nota, no con el poste mas cercano. De los demas, el generador no "
        "distingue uno que no esta en un poste de uno cuyo poste no ha sabido encontrar, asi que "
        "la fila sale con ENABLED = NO: si no esta en un poste, deja PROFILE_ID vacio y pon "
        "ENABLED = SI; si lo esta, escribe su PROFILE_ID y su VIA.",
    ),
    (
        "ESTACION",
        "El prefijo del nombre si es una estacion del maestro. Las zonas neutras, tuneles y "
        "subestaciones (KAF, TN3, HSA...) no lo son: en naranja va la estacion del plano mas "
        "cercana, y se corrige una vez por prefijo en ESTACION_POR_PREFIJO, no fila a fila. Un "
        "seccionador no tiene por que ser de una estacion: si no es de ninguna, escribe SIN "
        "ESTACION (esta al final del desplegable), aqui o en ESTACION_CORRECTA. En blanco no se "
        "carga: seria un olvido. Sin estacion y sin poste, su VIA es lo unico que lo situa, y "
        "tiene que ser una del EP. El seccionador se identifica por su EP y su NOMBRE: dos filas "
        "con el mismo nombre en el mismo EP salen con REVISAR = SI.",
    ),
    (
        "VIA SIN COMPROBAR",
        "El plano solo numera las vias en las estaciones, y con su numeracion, no con la del "
        "maestro (1/2 donde el maestro dice INT/EXT, por ejemplo). Si el maestro tiene dos postes "
        "con seccionador al mismo KP (uno por via) y la via del plano no dice cual es, hay que "
        "decirlo: el motivo nombra el otro.",
    ),
    (
        "VIA DISTINTA",
        "El plano y el poste no dicen la misma via, pero el KP casa a 10 m o menos: es una nota, "
        "no impide ENABLED = SI. Con el KP mas lejos, el motivo empieza por 'el DXF dice via' y "
        "la fila espera a una persona.",
    ),
    (
        "DISCONNECTOR_FUNCTION",
        "La del poste casado. En naranja, propuesta por lo que puentea en el plano (IO, SI), el "
        "nombre (NS, TE, FP) y on-load.",
    ),
    (
        "AISLADORES",
        "El plano no les pone nombre ni KP. NOMBRE es una propuesta (las agujas del escape). KP es "
        "una formula: el punto medio de KP_AGUJA_1 y KP_AGUJA_2 si estan a 300 m o menos; si se "
        "separan mas, la aguja 2 va con el KP de otra via y manda el de la aguja 1; con una sola, "
        "la que haya. Si lo puentea un seccionador rotulado, KP lleva el suyo escrito, y manda "
        "hasta que lo borres. Un numero escrito en KP manda; un aislador sin agujas lleva el KP a "
        "mano. KP_ESTIMADO_M es una pista para buscarlo (mediana de error 41 m, uno de cada cinco "
        "a mas de 370 m).",
    ),
    (
        "AGUJAS",
        "Van en la fila de su aislador: AGUJA_1, KP_AGUJA_1 y TANGENTE_1 son la del extremo del "
        "escape en VIA, y AGUJA_2, KP_AGUJA_2 y TANGENTE_2 la del extremo en VIA_CONECTADA; en un "
        "IN_TRACK, las dos son de VIA. Un aislador puede no tener agujas. Del plano salen el "
        "codigo W y la tangente 1:N (TANGENTE solo el denominador: 9 para 1:9); el KP no lo "
        "rotula, y va en amarillo: en metros, 84+490 = 84490.",
    ),
    (
        "LOCALIZAR EN AUTOCAD",
        'X, Y son coordenadas del plano (ZOOM C x,y 30). HANDLE: (handent "1BF43") selecciona '
        "la entidad.",
    ),
]


PLAN_NAME = "Operational disconnectors 28.07.25 (based on OCS-SCADA_PD_R38)"
REVIEWED_BOOK = os.path.join(DATA, "sectioning-review.xlsx")


def plan_problem(path):
    """Por que no se puede leer el plano, o None. El plano no viaja con el repositorio."""
    where = (
        "El plano de operacion de seccionadores (%r, exportado a DXF desde AutoCAD) no esta en el "
        "repositorio: pesa 46 MB y hay que pedirlo a quien mantiene el seccionamiento. Se pasa como primer "
        "argumento:\n    python3 data/tools/build_sectioning_review.py RUTA/plano.dxf -o libro.xlsx\n"
        "Sin el no se puede generar el libro de revision. Lo que si se puede sin el plano: "
        "recalcular RESUMEN y PENDIENTE de un libro ya escrito (--refresh LIBRO) y comprobarlas "
        "(--check LIBRO)." % PLAN_NAME
    )
    if not path:
        return "Falta el plano DXF. " + where
    if not os.path.isfile(path):
        return "No existe el plano DXF %r. %s" % (path, where)
    with open(path, "rb") as handle:
        head = handle.read(4096)
    # Un DXF de texto empieza por el par '0 / SECTION' (o un comentario 999); uno binario, por su
    # firma. Un .dwg, que es lo que guarda AutoCAD, no vale: hay que exportarlo.
    if head.startswith(b"AC10") and b"SECTION" not in head:
        return "%r es un DWG, no un DXF: exportalo a DXF desde AutoCAD (DXFOUT). %s" % (path, where)
    if not (head.startswith(b"AutoCAD Binary DXF") or b"SECTION" in head):
        return "%r no parece un DXF (no empieza por una seccion). %s" % (path, where)
    return None


def plan_fingerprint(path):
    """La huella del plano con el que se genera un libro: nombre, tamaño, fecha y sha256.

    Va al LEEME del libro (PLANO): con ella se sabe despues de que fichero salio, que es lo unico
    que permite repetirlo. El libro revisado de 2026 se genero antes de que se apuntara.
    """
    import datetime
    import hashlib

    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    stat = os.stat(path)
    modified = datetime.datetime.fromtimestamp(stat.st_mtime, datetime.timezone.utc)
    return {
        "name": os.path.basename(path),
        "bytes": stat.st_size,
        "modified": modified.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sha256": digest.hexdigest(),
    }


def plan_readme(fingerprint):
    return (
        "PLANO",
        "Generado desde %(name)s: %(bytes)d bytes, modificado el %(modified)s, sha256 %(sha256)s. "
        "El plano no esta en el repositorio; con esta huella se comprueba que el que se tiene es "
        "el mismo." % fingerprint,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("dxf", nargs="?", help="plano de seccionamiento en DXF")
    parser.add_argument(
        "--refresh",
        metavar="LIBRO",
        help="recalcula RESUMEN y PENDIENTE de un libro ya escrito, de sus hojas de datos, sin "
        "tocar el resto; no necesita el plano",
    )
    parser.add_argument(
        "--check",
        metavar="LIBRO",
        help="comprueba que RESUMEN y PENDIENTE de un libro casan con sus hojas de datos; termina "
        "con codigo distinto de cero si no",
    )
    parser.add_argument(
        "-m",
        "--master",
        default=os.path.join(DATA, "profile-master.xlsx"),
        help="maestro de perfiles (por defecto data/profile-master.xlsx)",
    )
    parser.add_argument(
        "-l",
        "--lovs",
        default=os.path.join(DATA, "lov-master.xlsx"),
        help="maestro de catalogos, para las funciones (por defecto data/lov-master.xlsx)",
    )
    parser.add_argument(
        "-o", "--output", default="sectioning-review.xlsx", help="libro de revision a escribir"
    )
    args = parser.parse_args(argv)

    if args.refresh or args.check:
        if args.dxf:
            parser.error("--refresh y --check trabajan sobre un libro ya escrito: sobra el plano")
        if args.refresh:
            lines, pending = refresh_review(args.refresh)
            print("%s: RESUMEN (%d lineas) y PENDIENTE (%d filas) recalculadas de sus hojas de "
                  "datos." % (args.refresh, len(lines), len(pending)))
        problems = check_review(args.check) if args.check else []
        for problem in problems[:20]:
            print(problem, file=sys.stderr)
        if problems:
            print(
                "%s: RESUMEN o PENDIENTE no casan con las hojas de datos (%d diferencias). "
                "Refrescalas con --refresh." % (args.check, len(problems)),
                file=sys.stderr,
            )
            return 1
        return 0
    problem = plan_problem(args.dxf)
    if problem:
        print(problem, file=sys.stderr)
        return 2
    if os.path.exists(args.output) and os.path.samefile(args.output, REVIEWED_BOOK):
        print(
            "%s es el libro ya revisado: lo que hay en el esta escrito a mano y generar otro lo "
            "pisaria. Escribe el nuevo en otra ruta (-o) y pasa a el la revision." % args.output,
            file=sys.stderr,
        )
        return 2

    fingerprint = plan_fingerprint(args.dxf)
    print("Leyendo %(name)s (%(bytes)d bytes, sha256 %(sha256)s) ..." % fingerprint)
    drawing = read_dxf(args.dxf)
    labels = match_labels(drawing)
    tracks = Tracks(drawing)
    switches = read_switches(drawing)
    plan_stations = read_stations(drawing)
    master = read_profile_master(args.master)
    disconnectors = build_disconnectors(drawing, labels, tracks, plan_stations, master)
    insulators, unused_sect_i = build_insulators(
        drawing, tracks, switches, plan_stations, disconnectors, master
    )

    d_rows, d_outside = disconnector_rows(disconnectors, master)
    i_rows, i_outside = insulator_rows(insulators, master)
    inventory = [
        {
            "HANDLE": r["handle"],
            "X": round(r["x"], 2),
            "Y": round(r["y"], 2),
            "CAPA": r["layer"],
            "BLOQUE": r["block"],
            "TIPO": r["type"],
            "ESTADO_DIBUJO": r["status"],
            "NOMBRE": r["name"],
            "ROTULO": r["label_text"],
            "KP_ROTULO": r["kp_txt"] or "",
            "KP_ROTULO_M": r["kp_m"] if r["kp_m"] is not None else "",
            "NOMBRES_EN_ROTULO": r["names_in_label"] or "",
            "ASOCIACION": r["how"],
            "CONFIANZA": r["confidence"],
            "ON_LOAD": "SI" if r["on_load"] else "NO",
            "ESTADO_NORMAL": r["state"],
            "DETECTOR_TENSION": "SI" if r["vd"] else "NO",
            "TRANSF_CORRIENTE": "SI" if r["ct"] else "NO",
            "CUADRO_VERDE_RTU": "SI" if r["rtu_square"] else "NO",
            "COD_ATRIBUTO": r["cod"],
            "PREFIJO": r["prefix"],
            "ESTACION_PLANO": r["plan_station"],
            "VIAS_DXF": ", ".join(r["tracks"]),
            "PUENTEA": r["bridged"],
            "FUNCION_POR_NOMBRE": r["name_function"],
            "EP": r["ep"] or "",
            "POSTE": (r.get("pole") or {}).get("profile", ""),
            "VIA_POSTE": (r.get("pole") or {}).get("via", ""),
            "DIF_KP_POSTE_M": round(r["pole_dkp"], 1) if r.get("pole") else "",
        }
        for r in sorted(disconnectors, key=lambda r: (r["status"] != EN_SERVICE, r["x"]))
    ]
    insulator_inventory = [
        {
            "HANDLE": r["handle"],
            "X": round(r["x"], 2),
            "Y": round(r["y"], 2),
            "CAPA": r["layer"],
            "ESTADO_DIBUJO": r["status"],
            "TIPO_INSTALACION": r["type"],
            "VIAS_DXF": " / ".join(t or "?" for t in r["tracks"]),
            "AGUJAS_DXF": " / ".join(
                "%s 1:%s" % (s["code"], s["tangent"] or "?") if s else "?" for s in r["switches"]
            ),
            "AGUJAS_EN_CADA_CRUCE": " | ".join("/".join(n) for n in r["switches_near"]),
            "PUENTEADO_POR": r.get("bridged_by", ""),
            "KP_ROTULO": r.get("kp_txt") or "",
            "EP": r.get("ep") or "",
            "ESTACION_PLANO": r["plan_station"],
        }
        for r in sorted(insulators, key=lambda r: (r["status"] != EN_SERVICE, r["x"]))
    ]
    orphans = []
    for label in labels:
        if label["kind"] not in ("device", "depot"):
            continue
        for k, m in enumerate(label.get("match") or []):
            if m is None:
                status = _drawing_status(label["layer"], label["x"], label["y"])
                orphans.append(
                    {
                        "NOMBRE": label["names"][k],
                        "ROTULO": label["text"].replace("\n", " | "),
                        "KP_ROTULO": label["kp_txt"] or "",
                        "ESTADO_DIBUJO": status,
                        "CAPA": label["layer"],
                        "X": round(label["x"], 2),
                        "Y": round(label["y"], 2),
                    }
                )
    orphan_name_options(d_rows, orphans)
    # Los seccionadores de una zona neutra, un tunel o una subestacion llevan el codigo de esa
    # instalacion (KAF, TN3, HSA), no el de una estacion. La estacion se decide una vez por
    # prefijo, no fila a fila. Un simbolo sin rotulo no tiene prefijo: se decide en su fila.
    groups = collections.defaultdict(list)
    for r in disconnectors:
        if r["ep"] and r["prefix"] and r["station_from"] == "cercania":
            groups[(r["prefix"], r["ep"], r["station"])].append(r["name"])
    by_prefix = [
        {
            "PREFIJO": prefix,
            "EP": ep,
            "ESTACION_PROPUESTA": station,
            "SECCIONADORES": len(names),
            "NOMBRES": ", ".join(sorted(names)[:8]) + (" ..." if len(names) > 8 else ""),
            "ESTACION_CORRECTA": "",
            "ESTACIONES_DEL_EP": " | ".join(sorted(master["stations"].get(ep, set()))),
        }
        for (prefix, ep, station), names in sorted(groups.items())
    ]
    used = {id(r["pole"]) for r in disconnectors if r.get("pole")}
    unused_ids = {id(p) for p in unused_sect_i}
    poles_left = [
        {
            "EP": p["ep"],
            "VIA": p["via"],
            "PROFILE_ID": p["profile"],
            "KP": p["kp"],
            "CODIGOS": "|".join(p["dcodes"] or p["scodes"]),
            "QUE_FALTA": "seccionador" if p["dcodes"] else "aislador (SECT-I)",
        }
        for p in master["profiles"]
        if (p["dcodes"] and id(p) not in used) or id(p) in unused_ids
    ]
    poles_left.sort(key=lambda d: (d["QUE_FALTA"], d["EP"], str(d["VIA"]), d["KP"]))

    # Las claves que empiezan por '_' (las propuestas y las opciones de cada fila) no son columnas.
    d_cols = DISCONNECTOR_COLUMNS + [
        k
        for k in (d_rows[0] if d_rows else {})
        if k not in DISCONNECTOR_COLUMNS and not k.startswith("_")
    ]
    i_cols = INSULATOR_COLUMNS + [
        k
        for k in (i_rows[0] if i_rows else {})
        if k not in INSULATOR_COLUMNS and not k.startswith("_")
    ]
    master_comments = {
        "PROFILE_ID": "Poste del maestro con seccionador a menos de 80 m del KP del rotulo; "
        "en naranja, el mas cercano sin codigo, con su KP en KP_POSTE. Vacio si el seccionador "
        "no esta en un poste.",
        "KP_POSTE": "KP del poste, en metros. Solo cuenta si el PROFILE_ID se repite (una via de "
        "dos tramos concatenados, o las dos vias de un poste de enlace): dice cual es. Si cambias "
        "de poste, cambialo tambien.",
        "VIA": "La via que maniobra: la del poste, o la otra si cuelga del poste de otra via (un "
        "haz de vias: talleres, cocheras); entonces el poste se busca en todo su EP.",
        "VIA_CONECTADA": "La otra via de uno que pone dos en paralelo (Disc/PP, LoadB/PP). En "
        "naranja, la del plano; en amarillo, una de puesta en paralelo sin ella. Nunca la suya (VIA, "
        "o la del poste si VIA esta vacia).",
        "ESTACION": "La del EP, o SIN ESTACION si no es de ninguna (en plena via, una zona "
        "neutra, una subestacion). En blanco no se carga.",
        "ON_LOAD": "SI: circulo medio relleno (on-load). NO: vacio (off-load).",
        "KP": "En metros, solo sin poste: el de uno en un poste es el de su perfil. Si quitas el "
        "poste de una fila, copia aqui KP_ROTULO_M.",
        "NORMALLY_OPEN": "Estado normal. SI: cuchilla dibujada abierta. NO: cerrada.",
        "DRIVE_TYPE": "MOTOR (circulo del accionamiento) o MANUAL.",
        "POSTES_CERCANOS": "Los postes del maestro a menos de 80 m del KP del rotulo: los del "
        "desplegable de PROFILE_ID, con su via, su KP, la distancia y sus codigos.",
        "DISCONNECTOR_FUNCTION": "Codigo del catalogo DisconnectorFunction (Disc/IO, LoadB/NS...).",
        "ENABLED": "SI solo con la fila completa y sin dudas.",
    }
    sheets = [
        (
            "DISCONNECTORS",
            d_cols,
            len(DISCONNECTOR_COLUMNS),
            {"EP", "ESTACION", "NOMBRE", "DISCONNECTOR_FUNCTION"},
            d_rows,
            master_comments,
        ),
        (
            "SECTION_INSULATORS",
            i_cols,
            len(INSULATOR_COLUMNS),
            {"ESTACION", "KP", "VIA"},
            i_rows,
            {
                "NOMBRE": "Propuesta: el plano no nombra los aisladores.",
                "KP": "En metros. Una formula con el KP de las agujas: el punto medio, o el de la "
                "aguja 1 si se separan mas de %d m. Un numero escrito manda; sin agujas, se escribe "
                "a mano." % SWITCH_KP_MAX_GAP_M,
                "VIA_CONECTADA": "Solo en TRACK_CONNECTION.",
                "AGUJA_1": "La aguja del extremo del escape en VIA: su codigo (W31), su KP en metros "
                "y la tangente (9 para 1:9). El plano no rotula el KP.",
                "AGUJA_2": "La aguja del extremo en VIA_CONECTADA. En un IN_TRACK, las dos son de "
                "VIA.",
            },
        ),
        (
            "ESTACION_POR_PREFIJO",
            [
                "PREFIJO",
                "EP",
                "ESTACION_PROPUESTA",
                "SECCIONADORES",
                "NOMBRES",
                "ESTACION_CORRECTA",
                "ESTACIONES_DEL_EP",
            ],
            0,
            {"ESTACION_CORRECTA"},
            by_prefix,
            {
                "ESTACION_CORRECTA": "Solo si la propuesta no vale: el codigo de estacion del "
                "maestro (columna ESTACIONES_DEL_EP), o SIN ESTACION si los de este prefijo no son "
                "de ninguna."
            },
        ),
        (
            "SECCIONADORES_FUERA_MAESTRO",
            list(d_outside[0].keys()) if d_outside else ["HANDLE"],
            0,
            set(),
            d_outside,
            None,
        ),
        (
            "AISLADORES_FUERA_MAESTRO",
            list(i_outside[0].keys()) if i_outside else ["HANDLE"],
            0,
            set(),
            i_outside,
            None,
        ),
        ("SECCIONADORES_DXF", list(inventory[0].keys()), 0, set(), inventory, None),
        (
            "AISLADORES_DXF",
            list(insulator_inventory[0].keys()),
            0,
            set(),
            insulator_inventory,
            None,
        ),
        (
            "ROTULOS_SIN_SIMBOLO",
            ["NOMBRE", "ROTULO", "KP_ROTULO", "ESTADO_DIBUJO", "CAPA", "X", "Y"],
            0,
            set(),
            orphans,
            None,
        ),
        (
            "POSTES_SIN_CASAR",
            ["EP", "VIA", "PROFILE_ID", "KP", "CODIGOS", "QUE_FALTA"],
            0,
            set(),
            poles_left,
            None,
        ),
    ]
    functions = read_disconnector_functions(args.lovs, master["profiles"])
    choices = {"lists": review_lists(master, functions), "columns": review_choices()}
    write_review(args.output, sheets, README + [plan_readme(fingerprint)], choices)

    print("\n%-34s%8s%10s" % ("HOJA", "FILAS", "ENABLED"))
    print(
        "%-34s%8d%10d"
        % ("DISCONNECTORS", len(d_rows), sum(1 for r in d_rows if r["ENABLED"] == "SI"))
    )
    print(
        "%-34s%8d%10d"
        % ("SECTION_INSULATORS", len(i_rows), sum(1 for r in i_rows if r["ENABLED"] == "SI"))
    )
    print(
        "%-34s%8d"
        % (
            "  agujas",
            sum(1 for r in i_rows for code, _, _ in INSULATOR_SWITCH_COLUMNS if r[code]),
        )
    )
    print("%-34s%8d" % ("ESTACION_POR_PREFIJO", len(by_prefix)))
    print("%-34s%8d" % ("SECCIONADORES_FUERA_MAESTRO", len(d_outside)))
    print("%-34s%8d" % ("AISLADORES_FUERA_MAESTRO", len(i_outside)))
    print("%-34s%8d" % ("ROTULOS_SIN_SIMBOLO", len(orphans)))
    print("Escrito: %s" % args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
