#!/usr/bin/env python3
"""svg2vd.py - convert the Mausam scene SVG subset into Android VectorDrawable XML.

Usage:
    python3 art/scene/svg2vd.py                # converts every art/scene/*.svg
    python3 art/scene/svg2vd.py foo.svg [...]  # converts the given files

Output goes to app/src/main/res/drawable/<name>.xml.

Supported SVG subset (deliberately small so the conversion is exact):
  * root <svg> with width/height/viewBox
  * <path d="...">  (d is passed through verbatim; VectorDrawable pathData
    follows the SVG path grammar)
  * <circle>, <ellipse>, <rect> (rx/ry optional) - converted to paths
  * fill="#RRGGBB" | fill="url(#id)" | fill="none", fill-opacity, fill-rule
  * <linearGradient>/<radialGradient> in <defs>, gradientUnits="userSpaceOnUse",
    <stop offset stop-color="#RRGGBB" stop-opacity>
No transforms, strokes, groups, filters, masks or text.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "http://www.w3.org/2000/svg"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_IN = os.path.join(ROOT, "art", "scene")
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
KAPPA = 0.5522847498307936


def tag(el):
    return el.tag.split('}')[-1]


def num(v):
    s = f"{float(v):.3f}".rstrip('0').rstrip('.')
    return '0' if s in ('', '-0') else s


def pct(v):
    v = str(v).strip()
    return float(v[:-1]) / 100.0 if v.endswith('%') else float(v)


def argb(hex_color, opacity=1.0):
    c = hex_color.strip()
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", c):
        raise ValueError(f"unsupported colour {c!r} (use #RRGGBB)")
    a = max(0, min(255, round(float(opacity) * 255)))
    return f"#{a:02X}{c[1:].upper()}"


def ellipse_path(cx, cy, rx, ry):
    kx, ky = rx * KAPPA, ry * KAPPA
    return (f"M{num(cx + rx)} {num(cy)}"
            f"C{num(cx + rx)} {num(cy + ky)} {num(cx + kx)} {num(cy + ry)} {num(cx)} {num(cy + ry)}"
            f"C{num(cx - kx)} {num(cy + ry)} {num(cx - rx)} {num(cy + ky)} {num(cx - rx)} {num(cy)}"
            f"C{num(cx - rx)} {num(cy - ky)} {num(cx - kx)} {num(cy - ry)} {num(cx)} {num(cy - ry)}"
            f"C{num(cx + kx)} {num(cy - ry)} {num(cx + rx)} {num(cy - ky)} {num(cx + rx)} {num(cy)}Z")


def rect_path(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f"M{num(x)} {num(y)}L{num(x + w)} {num(y)}L{num(x + w)} {num(y + h)}L{num(x)} {num(y + h)}Z"
    rx = min(rx or ry, w / 2); ry = min(ry or rx, h / 2)
    kx, ky = rx * KAPPA, ry * KAPPA
    return (f"M{num(x + rx)} {num(y)}L{num(x + w - rx)} {num(y)}"
            f"C{num(x + w - rx + kx)} {num(y)} {num(x + w)} {num(y + ry - ky)} {num(x + w)} {num(y + ry)}"
            f"L{num(x + w)} {num(y + h - ry)}"
            f"C{num(x + w)} {num(y + h - ry + ky)} {num(x + w - rx + kx)} {num(y + h)} {num(x + w - rx)} {num(y + h)}"
            f"L{num(x + rx)} {num(y + h)}"
            f"C{num(x + rx - kx)} {num(y + h)} {num(x)} {num(y + h - ry + ky)} {num(x)} {num(y + h - ry)}"
            f"L{num(x)} {num(y + ry)}"
            f"C{num(x)} {num(y + ry - ky)} {num(x + rx - kx)} {num(y)} {num(x + rx)} {num(y)}Z")


def collect_gradients(root):
    grads = {}
    for el in root.iter():
        t = tag(el)
        if t not in ("linearGradient", "radialGradient"):
            continue
        if el.get("gradientUnits", "objectBoundingBox") != "userSpaceOnUse":
            raise ValueError(f"gradient {el.get('id')}: only gradientUnits=userSpaceOnUse is supported")
        if el.get("gradientTransform"):
            raise ValueError(f"gradient {el.get('id')}: gradientTransform is not supported")
        stops = []
        for st in el:
            if tag(st) != "stop":
                continue
            stops.append((pct(st.get("offset", "0")), st.get("stop-color", "#000000"),
                          float(st.get("stop-opacity", "1"))))
        if t == "linearGradient":
            grads[el.get("id")] = dict(type="linear", x1=float(el.get("x1", 0)), y1=float(el.get("y1", 0)),
                                       x2=float(el.get("x2", 0)), y2=float(el.get("y2", 0)), stops=stops)
        else:
            if el.get("fx") or el.get("fy"):
                raise ValueError(f"gradient {el.get('id')}: focal points are not supported by VectorDrawable")
            grads[el.get("id")] = dict(type="radial", cx=float(el.get("cx", 0)), cy=float(el.get("cy", 0)),
                                       r=float(el.get("r", 0)), stops=stops)
    return grads


def shape_to_pathdata(el):
    t = tag(el)
    g = lambda k, d="0": float(el.get(k, d))
    if t == "path":
        return el.get("d", "").strip()
    if t == "circle":
        return ellipse_path(g("cx"), g("cy"), g("r"), g("r"))
    if t == "ellipse":
        return ellipse_path(g("cx"), g("cy"), g("rx"), g("ry"))
    if t == "rect":
        rx = el.get("rx"); ry = el.get("ry")
        rx = float(rx) if rx is not None else (float(ry) if ry is not None else 0.0)
        ry = float(ry) if ry is not None else rx
        return rect_path(g("x"), g("y"), g("width"), g("height"), rx, ry)
    return None


def gradient_xml(gr, opacity):
    lines = []
    if gr["type"] == "linear":
        lines.append(f'            <gradient android:type="linear" android:startX="{num(gr["x1"])}" '
                     f'android:startY="{num(gr["y1"])}" android:endX="{num(gr["x2"])}" android:endY="{num(gr["y2"])}">')
    else:
        lines.append(f'            <gradient android:type="radial" android:centerX="{num(gr["cx"])}" '
                     f'android:centerY="{num(gr["cy"])}" android:gradientRadius="{num(gr["r"])}">')
    for off, col, a in gr["stops"]:
        lines.append(f'                <item android:offset="{num(off)}" android:color="{argb(col, a * opacity)}"/>')
    lines.append('            </gradient>')
    return "\n".join(lines)


def convert(svg_path, out_dir):
    tree = ET.parse(svg_path)
    root = tree.getroot()
    if tag(root) != "svg":
        raise ValueError("not an svg root")
    vb = root.get("viewBox")
    if vb:
        _, _, vw, vh = [float(v) for v in vb.replace(',', ' ').split()]
    else:
        vw, vh = float(root.get("width")), float(root.get("height"))
    grads = collect_gradients(root)

    for el in root.iter():
        if el.get("transform") or el.get("stroke") not in (None, "none"):
            raise ValueError(f"<{tag(el)}> uses transform/stroke, which this converter does not support")
        if tag(el) in ("g", "text", "filter", "mask", "clipPath", "use", "image"):
            raise ValueError(f"<{tag(el)}> is not supported")

    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt"',
        f'    android:width="{num(vw)}dp"',
        f'    android:height="{num(vh)}dp"',
        f'    android:viewportWidth="{num(vw)}"',
        f'    android:viewportHeight="{num(vh)}">',
    ]
    count = 0
    for el in root:  # document order = paint order
        d = shape_to_pathdata(el)
        if d is None:
            continue
        fill = el.get("fill", "#000000").strip()
        if fill == "none":
            continue
        opacity = float(el.get("fill-opacity", "1"))
        fill_type = ' android:fillType="evenOdd"' if el.get("fill-rule") == "evenodd" else ""
        m = re.fullmatch(r"url\(#([^)]+)\)", fill)
        if m:
            gr = grads.get(m.group(1))
            if gr is None:
                raise ValueError(f"unknown gradient {fill}")
            out.append(f'    <path{fill_type}')
            out.append(f'        android:pathData="{d}">')
            out.append('        <aapt:attr name="android:fillColor">')
            out.append(gradient_xml(gr, opacity))
            out.append('        </aapt:attr>')
            out.append('    </path>')
        else:
            out.append(f'    <path{fill_type}')
            out.append(f'        android:fillColor="{argb(fill, opacity)}"')
            out.append(f'        android:pathData="{d}"/>')
        count += 1
    out.append('</vector>')
    name = os.path.splitext(os.path.basename(svg_path))[0]
    dest = os.path.join(out_dir, name + ".xml")
    with open(dest, "w") as f:
        f.write("\n".join(out) + "\n")
    print(f"{os.path.relpath(svg_path, ROOT)} -> {os.path.relpath(dest, ROOT)}  ({count} paths, {vw:g}x{vh:g})")


def main(argv):
    files = [a for a in argv if a.endswith(".svg")]
    if not files:
        files = sorted(os.path.join(DEFAULT_IN, f) for f in os.listdir(DEFAULT_IN) if f.endswith(".svg"))
    os.makedirs(DEFAULT_OUT, exist_ok=True)
    for f in files:
        convert(f, DEFAULT_OUT)


if __name__ == "__main__":
    main(sys.argv[1:])
