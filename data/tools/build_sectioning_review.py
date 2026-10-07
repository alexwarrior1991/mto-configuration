#!/usr/bin/env python3
"""Saca los seccionadores y los aisladores de seccion del plano DXF de seccionamiento.

Que hace
--------
Lee el plano de operacion ("Operational disconnectors 28.07.25 (based on OCS-SCADA_PD_R38)",
exportado a DXF) y escribe un libro de REVISION con tres hojas que tienen exactamente las
columnas de las costuras de profile-master.xlsx (DISCONNECTORS, SECTION_INSULATORS y
SECTION_INSULATOR_SWITCHES), cruzadas con los postes del propio maestro. Una persona completa
y valida ese libro; nada de lo que sale de aqui va directo a la aplicacion.

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

El DXF no esta en el repositorio (46 MB; ver "El tamaño de workbook/" en data/README.md).
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
# Hamifratz) es de EP futuros. Lo que cae dentro no se carga.
HAIFA_DEPOT_BOX = (-1000.0, -4300.0, 2400.0, -3600.0)

# Estados de dibujo. Solo EN_SERVICE entra en las hojas del maestro: los futuros, los de via no
# electrificada y el deposito de Haifa son de EP todavia no construidos, y la zona neutra de
# Holtz esta dibujada como deshabilitada.
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
SECT_I_MATCH_MAX_M = 120.0
SWITCH_TIE = 3.0  # dos rotulos de aguja a menos de 3 unidades de diferencia: empate

# Prefijos del rotulo que no coinciden con el codigo de estacion del maestro.
STATION_ALIAS = {"ASKD": "ASK-D", "ASKS": "ASK-S", "DLOD": "LOD_D", "LOD": "LOD_S", "MSM": "MSMR"}

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


def function_proposal(kind, on_load, name_function, bridged):
    """Codigo del catalogo DisconnectorFunction cuando no hay poste del que copiarlo."""
    if kind == "SECCIONADOR DE PUESTA A TIERRA" or name_function == "TE":
        return "ED"
    if name_function == "NS":
        return "LoadB/NS" if on_load else "Disc/NS"
    if bridged == "IO":
        return "LoadB/IO" if on_load else "Disc/IO"
    if bridged == "SI" and not on_load:
        return "Disc/SI"
    return ""


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

    neighbours: [(x, kp)]. El plano no esta a escala; en las pruebas contra los aisladores
    con KP conocido el error mediano es de ~20 m, pero uno de cada cinco se va de mas de 70 m.
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
    """'BINYAMINA (BIN)\\n28' en la capa de estaciones: codigo, nombre y posicion."""
    stations = []
    for t in drawing["texts"]:
        if t["layer"] != "0_Stations":
            continue
        m = re.search(r"\(([A-Z][A-Z_\-]{1,6})\)", t["text"].replace("\n", " "))
        if m:
            stations.append(
                {
                    "code": m.group(1),
                    "name": re.sub(r"\s+", " ", t["text"].split("(")[0]).strip(),
                    "x": t["x"],
                    "y": t["y"],
                }
            )
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
                "dcodes": [c for c in codes if c.startswith(("Disc", "LoadB", "ED"))],
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
    # EP cubre el KP. Los de lineas que el maestro no tiene se quedan sin EP.
    for r in records:
        r["ep"] = master["station_ep"].get(r["prefix"])
        r["ep_from"] = "prefijo" if r["ep"] else ""
    placed = [r for r in records if r["ep"]]
    for r in records:
        if r["ep"] or r["kp_m"] is None or r["status"] != EN_SERVICE:
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

    # Poste: asignacion 1:1 por EP contra los postes con codigo de seccionador. Coste = metros de
    # diferencia de KP, +60 si la via del plano no es la del poste, +25 si on-load no casa con
    # LoadB/Disc, +40 si es de puesta a tierra y el poste no tiene ED.
    by_ep = collections.defaultdict(list)
    for i, r in enumerate(records):
        if r["ep"] and r["kp_m"] is not None and r["status"] == EN_SERVICE:
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
                dk = abs(p["kp"] - r["kp_m"])
                if dk > POLE_MATCH_MAX_M:
                    continue
                c = dk
                if r["tracks"] and p["track"] and p["track"] not in r["tracks"]:
                    c += 60
                load_break = any(x.startswith("LoadB") for x in p["dcodes"])
                if r["type"] == "SECCIONADOR DE PUESTA A TIERRA":
                    c += 0 if any("ED" in x for x in p["dcodes"]) else 40
                elif load_break != r["on_load"]:
                    c += 25
                cost[a, b] = c
        rows, cols = linear_sum_assignment(cost)
        for a, b in zip(rows, cols):
            if cost[a, b] < 1e6:
                r, p = records[idx[a]], poles[b]
                r["pole"] = p
                r["pole_dkp"] = p["kp"] - r["kp_m"]

    # Sin poste con codigo: el poste mas cercano de una via compatible, si esta a menos de 30 m.
    # El maestro trae el poste pero sin el seccionador en SECTIONING_FEEDING (lleva FS-1, o nada).
    profiles_by_ep = collections.defaultdict(list)
    for p in master["profiles"]:
        profiles_by_ep[p["ep"]].append(p)
    for r in records:
        if r.get("pole") or not r["ep"] or r["kp_m"] is None or r["status"] != EN_SERVICE:
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

    labelled = [r for r in disconnectors if r["kp_m"] is not None and r["status"] == EN_SERVICE]
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
                ep=b["ep"],
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
        if not r.get("ep") and around:
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
        if r.get("kp_m") is not None and r.get("ep") and r["status"] == EN_SERVICE:
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
    "PROFILE_ID",
    "NOMBRE",
    "ON_LOAD",
    "DISCONNECTOR_FUNCTION",
    "ENABLED",
]
INSULATOR_COLUMNS = [
    "EP",
    "ESTACION",
    "NOMBRE",
    "KP",
    "TIPO_INSTALACION",
    "VIA",
    "VIA_CONECTADA",
    "ENABLED",
]
SWITCH_COLUMNS = ["EP", "ESTACION", "AISLADOR", "CODIGO", "KP", "TANGENTE", "VIA", "ENABLED"]

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
)


def disconnector_rows(records, master):
    rows, outside = [], []
    for r in records:
        if r["status"] != EN_SERVICE:
            continue
        reasons, proposals = [], set()
        pole = r.get("pole")
        if not r["ep"]:
            outside.append(r)
            continue
        via = pole["via"] if pole else ""
        profile = pole["profile"] if pole else ""
        if not pole and r.get("nearest_pole") and r["nearest_pole_d"] <= POLE_PROPOSAL_MAX_M:
            near = r["nearest_pole"]
            via, profile = near["via"], near["profile"]
            proposals.update({"VIA", "PROFILE_ID"})
            reasons.append(
                "poste propuesto: el mas cercano por KP (a %.0f m), sin seccionador en "
                "SECTIONING_FEEDING (%s)"
                % (r["nearest_pole_d"], "|".join(near["codes"]) or "vacio")
            )
        elif not pole:
            far = (
                (" (el mas cercano esta a %.0f m)" % r["nearest_pole_d"])
                if r.get("nearest_pole")
                else ""
            )
            reasons.append("sin poste en el maestro cerca del KP" + far)
        station = r["station"]
        if r["station_from"] == "cercania":
            proposals.add("ESTACION")
            reasons.append("estacion propuesta: la del plano mas cercana")
        if not via and r["tracks"]:
            via, why = master_track(master["tracks"].get(r["ep"], []), station, r["tracks"][0])
            if why:
                reasons.append(why)
        function = pole["dcodes"][0] if pole else ""
        if not function:
            function = function_proposal(r["type"], r["on_load"], r["name_function"], r["bridged"])
            if function:
                proposals.add("DISCONNECTOR_FUNCTION")
                reasons.append("funcion propuesta desde el plano (%s)" % function)
        if not r["name"]:
            reasons.append("simbolo sin rotulo: falta el nombre")
        if r["kp_m"] is None and r["name"]:
            reasons.append("rotulo sin KP (%s)" % (r["kp_txt"] or "ninguno"))
        if r["names_in_label"] > 1:
            reasons.append("etiqueta con %d nombres: comprobar cual es cual" % r["names_in_label"])
        validated = pole is not None and abs(r["pole_dkp"]) <= 10
        if r["confidence"] == "C" and not validated:
            reasons.append("rotulo lejos del simbolo")
        if pole and not r["tracks"]:
            # El plano solo numera las vias en las estaciones. En plena via, con dos postes iguales
            # al mismo KP (uno por via), la asignacion no tiene con que elegir: se dice.
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
                    "via sin comprobar: el plano no numera la via aqui y el maestro tiene otro "
                    "poste al mismo KP en %s"
                    % ", ".join("%s (%s)" % (t["via"], t["profile"]) for t in twins[:3])
                )
        if pole:
            if abs(r["pole_dkp"]) > 30:
                reasons.append("el poste esta a %.0f m del KP del rotulo" % r["pole_dkp"])
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
        complete = all([r["ep"], station, profile, r["name"], function, r["kp_m"] is not None])
        enabled = "SI" if complete and not any(x.startswith(BLOCKING) for x in reasons) else "NO"
        rows.append(
            {
                "EP": r["ep"],
                "ESTACION": station,
                "VIA": via,
                "PROFILE_ID": profile,
                "NOMBRE": r["name"],
                "ON_LOAD": "SI" if r["on_load"] else "NO",
                "DISCONNECTOR_FUNCTION": function,
                "ENABLED": enabled,
                "REVISAR": "SI" if reasons else "NO",
                "MOTIVO_REVISAR": "; ".join(reasons),
                "KP_ROTULO": r["kp_txt"] or "",
                "KP_ROTULO_M": r["kp_m"] if r["kp_m"] is not None else "",
                "KP_POSTE_M": (pole or {}).get("kp", ""),
                "DIF_KP_M": round(r["pole_dkp"], 1) if pole else "",
                "CODIGO_POSTE": "|".join(pole["dcodes"]) if pole else "",
                "TIPO_DXF": r["type"],
                "ESTADO_NORMAL": r["state"],
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
            }
        )
    # Un mismo poste fisico aparece en dos hojas de via del maestro (mismo PROFILE_ID y KP): los
    # dos seccionadores casados con el se señalan, porque uno de los dos sobra.
    twins = collections.Counter(
        (d["EP"], d["PROFILE_ID"], d["KP_POSTE_M"])
        for d in rows
        if d["PROFILE_ID"] and d["KP_POSTE_M"] != ""
    )
    for d in rows:
        key = (d["EP"], d["PROFILE_ID"], d["KP_POSTE_M"])
        if d["PROFILE_ID"] and d["KP_POSTE_M"] != "" and twins[key] > 1:
            others = [
                o["NOMBRE"]
                for o in rows
                if o is not d and (o["EP"], o["PROFILE_ID"], o["KP_POSTE_M"]) == key
            ]
            d["MOTIVO_REVISAR"] = "; ".join(
                x
                for x in [
                    d["MOTIVO_REVISAR"],
                    "el maestro repite el poste %s en otra via; tambien casado con %s"
                    % (d["PROFILE_ID"], ", ".join(others)),
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
    rows, switch_rows, outside = [], [], []
    used_names = collections.Counter()
    for r in records:
        if r["status"] != EN_SERVICE:
            continue
        if not r.get("ep"):
            outside.append(r)
            continue
        ep, station, kind = r["ep"], r["station"], r["type"]
        tracks_of_ep = master["tracks"].get(ep, [])
        reasons = []
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
            reasons.append("KP sin rotular en el plano")
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
                "X": round(r["x"], 2),
                "Y": round(r["y"], 2),
                "HANDLE": r["handle"],
                "_proposals": {"NOMBRE"}
                | ({"ESTACION"} if r.get("station_from") == "cercania" else set()),
            }
        )
        if kind == "TRACK_CONNECTION":
            seen = set()
            for k, s in enumerate(r["switches"]):
                if not s or s["code"] in seen:
                    continue
                seen.add(s["code"])
                side = sides[k] if k < len(sides) else None
                track_name, why = master_track(tracks_of_ep, station, side)
                notes = []
                if not s["tangent"]:
                    notes.append("tangente sin rotular")
                if why:
                    notes.append(why)
                if k < len(r["switches_near"]) and len(r["switches_near"][k]) > 1:
                    notes.append(
                        "otras agujas en el mismo cruce: %s" % "/".join(r["switches_near"][k])
                    )
                switch_rows.append(
                    {
                        "EP": ep,
                        "ESTACION": station,
                        "AISLADOR": name,
                        "CODIGO": s["code"],
                        "KP": "",
                        "TANGENTE": s["tangent"] or "",
                        "VIA": track_name,
                        "ENABLED": "SI",
                        "REVISAR": "SI" if notes else "NO",
                        "MOTIVO_REVISAR": "; ".join(notes),
                        "VIA_DXF": side or "",
                        "X_AISLADOR": round(r["x"], 2),
                        "Y_AISLADOR": round(r["y"], 2),
                        "HANDLE_AISLADOR": r["handle"],
                        "_proposals": {"AISLADOR"},
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
    switch_rows.sort(key=lambda d: (d["EP"], d["ESTACION"], d["AISLADOR"], d["CODIGO"]))
    outside_rows = [
        {
            "TIPO_INSTALACION": r["type"],
            "VIAS_DXF": " / ".join(t or "?" for t in r["tracks"]),
            "AGUJAS_DXF": " / ".join(
                "%s 1:%s" % (s["code"], s["tangent"] or "?") if s else "?" for s in r["switches"]
            ),
            "ESTACION_PLANO": r["plan_station"],
            "X": round(r["x"], 2),
            "Y": round(r["y"], 2),
            "HANDLE": r["handle"],
        }
        for r in outside
    ]
    return rows, switch_rows, outside_rows


def write_review(path, sheets_data, readme):
    import openpyxl
    from openpyxl.comments import Comment
    from openpyxl.styles import Alignment, Font, PatternFill
    from openpyxl.utils import get_column_letter

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
    summary = wb.create_sheet("RESUMEN")
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
            for j, h in enumerate(columns, 1):
                v = row.get(h, "")
                c = ws.cell(row=i, column=j, value="" if v is None else v)
                c.font = body
                if h in proposals and v not in ("", None):
                    c.fill = proposal
                elif h in required and v in ("", None):
                    c.fill = missing
                elif h == "REVISAR" and v == "SI":
                    c.fill = review
                elif h == "ENABLED" and v == "SI":
                    c.fill = ready
        ws.freeze_panes = "A2"
        ws.auto_filter.ref = "A1:%s%d" % (get_column_letter(len(columns)), max(1, len(data) + 1))
        for j, h in enumerate(columns, 1):
            width = max(
                [len(h)]
                + [
                    len(str(ws.cell(row=i, column=j).value or ""))
                    for i in range(2, min(len(data) + 2, 400))
                ]
            )
            ws.column_dimensions[get_column_letter(j)].width = min(60, max(8, width + 2))
        ws.row_dimensions[1].height = 30

    # Recuentos con formula: se recalculan cuando se edita el libro. '?*' cuenta textos no
    # vacios, cabecera incluida, de ahi el -1.
    def col(sheet, header):
        columns = next(c for t, c, *_ in sheets_data if t == sheet)
        letter = get_column_letter(columns.index(header) + 1)
        return "%s!$%s:$%s" % (sheet, letter, letter)

    lines = [
        ("Seccionadores dibujados (todos)", "=COUNTA(%s)-1" % col("SECCIONADORES_DXF", "HANDLE")),
        (
            "  en servicio",
            '=COUNTIF(%s,"%s")' % (col("SECCIONADORES_DXF", "ESTADO_DIBUJO"), EN_SERVICE),
        ),
        (
            "  de EP futuros, deshabilitados o en via no electrificada",
            "=COUNTA(%s)-1-B3" % col("SECCIONADORES_DXF", "HANDLE"),
        ),
        (
            "En servicio en lineas del maestro (hoja DISCONNECTORS)",
            "=COUNTA(%s)-1" % col("DISCONNECTORS", "HANDLE"),
        ),
        (
            "  con poste casado o propuesto (PROFILE_ID)",
            '=COUNTIF(%s,"?*")-1' % col("DISCONNECTORS", "PROFILE_ID"),
        ),
        (
            "  listos para cargar (ENABLED = SI)",
            '=COUNTIF(%s,"SI")' % col("DISCONNECTORS", "ENABLED"),
        ),
        ("  a revisar (REVISAR = SI)", '=COUNTIF(%s,"SI")' % col("DISCONNECTORS", "REVISAR")),
        (
            "  prefijos con estacion propuesta (hoja ESTACION_POR_PREFIJO)",
            "=COUNTA(%s)-1" % col("ESTACION_POR_PREFIJO", "PREFIJO"),
        ),
        (
            "En servicio en lineas que el maestro no tiene",
            "=COUNTA(%s)-1" % col("SECCIONADORES_FUERA_MAESTRO", "HANDLE"),
        ),
        ("Aisladores dibujados (todos)", "=COUNTA(%s)-1" % col("AISLADORES_DXF", "HANDLE")),
        (
            "  en lineas del maestro (hoja SECTION_INSULATORS)",
            "=COUNTA(%s)-1" % col("SECTION_INSULATORS", "HANDLE"),
        ),
        ("    con KP", '=COUNTIF(%s,">0")' % col("SECTION_INSULATORS", "KP")),
        ("    ENABLED = SI", '=COUNTIF(%s,"SI")' % col("SECTION_INSULATORS", "ENABLED")),
        (
            "  en lineas que el maestro no tiene",
            "=COUNTA(%s)-1" % col("AISLADORES_FUERA_MAESTRO", "HANDLE"),
        ),
        ("Agujas (filas)", "=COUNTA(%s)-1" % col("SECTION_INSULATOR_SWITCHES", "HANDLE_AISLADOR")),
        ("  con tangente", '=COUNTIF(%s,">0")' % col("SECTION_INSULATOR_SWITCHES", "TANGENTE")),
        (
            "Rotulos de seccionador sin simbolo",
            "=COUNTA(%s)-1" % col("ROTULOS_SIN_SIMBOLO", "ROTULO"),
        ),
        ("Postes del maestro sin casar", "=COUNTA(%s)-1" % col("POSTES_SIN_CASAR", "PROFILE_ID")),
    ]
    for j, h in enumerate(("Concepto", "Cantidad"), 1):
        c = summary.cell(row=1, column=j, value=h)
        c.font, c.fill = header_font, master_fill
    for i, (label, formula) in enumerate(lines, 2):
        summary.cell(row=i, column=1, value=label).font = Font(
            name=arial, size=10, bold=not label.startswith(" ")
        )
        summary.cell(row=i, column=2, value=formula).font = body
    summary.column_dimensions["A"].width = 60
    summary.column_dimensions["B"].width = 12

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


README = [
    (
        "QUE ES",
        "Seccionadores y aisladores de seccion del plano de operacion (DXF), cruzados con "
        "data/profile-master.xlsx. Lo genera data/tools/build_sectioning_review.py; "
        "data/README.md explica como.",
    ),
    (
        "HOJAS DEL MAESTRO",
        "DISCONNECTORS, SECTION_INSULATORS y SECTION_INSULATOR_SWITCHES llevan primero, con "
        "cabecera azul, exactamente las columnas de esas hojas en profile-master.xlsx. Las de "
        "cabecera gris son para revisar.",
    ),
    (
        "QUE ENTRA",
        "Solo lo dibujado en servicio en lineas que el maestro tiene. Los EP futuros (deposito "
        "de Haifa/Kishon, equipos futuros, via no electrificada) y la zona neutra deshabilitada "
        "de Holtz no entran; las lineas que el maestro no tiene van a "
        "SECCIONADORES_FUERA_MAESTRO y AISLADORES_FUERA_MAESTRO.",
    ),
    (
        "QUE EDITAR",
        "Amarillo = falta; naranja = propuesta del generador, a confirmar. Revisa cada fila con "
        "REVISAR = SI segun MOTIVO_REVISAR, corrige lo necesario y pon ENABLED = SI. Solo las "
        "filas con ENABLED = SI se cargarian.",
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
        "ESTADO_NORMAL",
        "Cuchilla alineada con sus contactos = CERRADO; inclinada = ABIERTO. Informativo: "
        "Disconnector no lo guarda.",
    ),
    (
        "PROFILE_ID",
        "Poste del maestro con seccionador en SECTIONING_FEEDING, mismo EP, a menos de 80 m del "
        "KP, preferente en la misma via y del mismo tipo. En naranja: el poste mas cercano (a "
        "menos de 30 m) cuando ninguno lleva el codigo.",
    ),
    (
        "ESTACION",
        "El prefijo del nombre si es una estacion del maestro. Las zonas neutras, tuneles y "
        "subestaciones (KAF, TN3, HSA...) no lo son: en naranja va la estacion del plano mas "
        "cercana, y se corrige una vez por prefijo en ESTACION_POR_PREFIJO, no fila a fila.",
    ),
    (
        "VIA SIN COMPROBAR",
        "El plano solo numera las vias en las estaciones. En plena via, si el maestro tiene dos "
        "postes con seccionador al mismo KP (uno por via), hay que decir cual es: el motivo "
        "nombra el otro.",
    ),
    (
        "DISCONNECTOR_FUNCTION",
        "La del poste casado. En naranja, propuesta por lo que puentea en el plano (IO, SI), el "
        "nombre (NS, TE) y on-load.",
    ),
    (
        "AISLADORES",
        "El plano no les pone nombre ni KP. NOMBRE es una propuesta (las agujas del escape). KP "
        "solo cuando un seccionador rotulado lo puentea; KP_ESTIMADO_M es una pista para "
        "buscarlo (mediana de error ~20 m, uno de cada cinco a mas de 70 m).",
    ),
    (
        "AGUJAS",
        "Codigo W y tangente 1:N de cada extremo del escape. El plano no rotula su KP.",
    ),
    (
        "LOCALIZAR EN AUTOCAD",
        'X, Y son coordenadas del plano (ZOOM C x,y 30). HANDLE: (handent "1BF43") selecciona '
        "la entidad.",
    ),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("dxf", help="plano de seccionamiento en DXF")
    parser.add_argument(
        "-m",
        "--master",
        default=os.path.join(DATA, "profile-master.xlsx"),
        help="maestro de perfiles (por defecto data/profile-master.xlsx)",
    )
    parser.add_argument(
        "-o", "--output", default="sectioning-review.xlsx", help="libro de revision a escribir"
    )
    args = parser.parse_args()

    print("Leyendo %s ..." % args.dxf)
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
    i_rows, s_rows, i_outside = insulator_rows(insulators, master)
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
    # Los seccionadores de una zona neutra, un tunel o una subestacion llevan el codigo de esa
    # instalacion (KAF, TN3, HSA), no el de una estacion. La estacion se decide una vez por
    # prefijo, no fila a fila.
    groups = collections.defaultdict(list)
    for r in disconnectors:
        if r["status"] == EN_SERVICE and r["ep"] and r["station_from"] == "cercania":
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

    d_cols = DISCONNECTOR_COLUMNS + [
        k
        for k in (d_rows[0] if d_rows else {})
        if k not in DISCONNECTOR_COLUMNS and k != "_proposals"
    ]
    i_cols = INSULATOR_COLUMNS + [
        k for k in (i_rows[0] if i_rows else {}) if k not in INSULATOR_COLUMNS and k != "_proposals"
    ]
    s_cols = SWITCH_COLUMNS + [
        k for k in (s_rows[0] if s_rows else {}) if k not in SWITCH_COLUMNS and k != "_proposals"
    ]
    master_comments = {
        "PROFILE_ID": "Poste del maestro con seccionador a menos de 80 m del KP del rotulo; "
        "en naranja, el mas cercano sin codigo.",
        "ON_LOAD": "SI: circulo medio relleno (on-load). NO: vacio (off-load).",
        "DISCONNECTOR_FUNCTION": "Codigo del catalogo DisconnectorFunction (Disc/IO, LoadB/NS...).",
        "ENABLED": "SI solo con la fila completa y sin dudas.",
    }
    sheets = [
        (
            "DISCONNECTORS",
            d_cols,
            len(DISCONNECTOR_COLUMNS),
            {"EP", "ESTACION", "VIA", "PROFILE_ID", "NOMBRE", "DISCONNECTOR_FUNCTION"},
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
                "KP": "En metros. Solo cuando lo puentea un seccionador rotulado.",
                "VIA_CONECTADA": "Solo en TRACK_CONNECTION.",
            },
        ),
        (
            "SECTION_INSULATOR_SWITCHES",
            s_cols,
            len(SWITCH_COLUMNS),
            {"ESTACION", "KP", "TANGENTE", "VIA"},
            s_rows,
            {
                "KP": "El plano no rotula el KP de las agujas.",
                "TANGENTE": "Solo el denominador: 9 para 1:9.",
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
                "maestro (columna ESTACIONES_DEL_EP)."
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
    write_review(args.output, sheets, README)

    print("\n%-34s%8s%10s" % ("HOJA", "FILAS", "ENABLED"))
    print(
        "%-34s%8d%10d"
        % ("DISCONNECTORS", len(d_rows), sum(1 for r in d_rows if r["ENABLED"] == "SI"))
    )
    print(
        "%-34s%8d%10d"
        % ("SECTION_INSULATORS", len(i_rows), sum(1 for r in i_rows if r["ENABLED"] == "SI"))
    )
    print("%-34s%8d" % ("SECTION_INSULATOR_SWITCHES", len(s_rows)))
    print("%-34s%8d" % ("ESTACION_POR_PREFIJO", len(by_prefix)))
    print("%-34s%8d" % ("SECCIONADORES_FUERA_MAESTRO", len(d_outside)))
    print("%-34s%8d" % ("AISLADORES_FUERA_MAESTRO", len(i_outside)))
    print("%-34s%8d" % ("ROTULOS_SIN_SIMBOLO", len(orphans)))
    print("Escrito: %s" % args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
