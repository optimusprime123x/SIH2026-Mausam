#!/usr/bin/env python3
"""
svg2vd.py - convert the tiny SVG subset used by art/spot/*.svg into Android
VectorDrawable XML.

Supported SVG subset (deliberately small, everything else is an error):
  * root <svg viewBox="0 0 W H">  (width/height in dp are taken from the viewBox)
  * <defs> containing <linearGradient>/<radialGradient> with
    gradientUnits="userSpaceOnUse" and <stop offset stop-color stop-opacity>
  * <g id="..."> without transform  -> <group android:name="...">
  * <path d="...">       (d is passed through verbatim)
  * <circle cx cy r>, <ellipse cx cy rx ry>, <rect x y width height rx [ry]>
    (all converted to path data)
  * fill="#rgb|#rrggbb|url(#id)|none", fill-opacity, opacity, fill-rule

Usage:
  python3 art/spot/svg2vd.py art/spot/*.svg -o app/src/main/res/drawable/
  python3 art/spot/svg2vd.py in.svg out.xml
"""
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "http://www.w3.org/2000/svg"
XLINK_NS = "http://www.w3.org/1999/xlink"


def local(tag):
    return tag.split("}", 1)[1] if "}" in tag else tag


def fmt(v):
    """Format a float compactly: 12.0 -> '12', 1.50 -> '1.5'."""
    s = ("%.3f" % float(v)).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def parse_color(s):
    s = s.strip()
    m = re.fullmatch(r"#([0-9a-fA-F]{3})", s)
    if m:
        return tuple(int(c * 2, 16) for c in m.group(1))
    m = re.fullmatch(r"#([0-9a-fA-F]{6})", s)
    if m:
        h = m.group(1)
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    raise ValueError("unsupported colour %r (use #rgb / #rrggbb)" % s)


def argb(rgb, alpha):
    a = max(0, min(255, int(round(alpha * 255))))
    return "#%02X%02X%02X%02X" % ((a,) + tuple(rgb))


def style_attrs(el):
    """Merge presentation attributes with an inline style="" declaration."""
    attrs = dict(el.attrib)
    style = attrs.pop("style", None)
    if style:
        for decl in style.split(";"):
            if ":" in decl:
                k, v = decl.split(":", 1)
                attrs[k.strip()] = v.strip()
    return attrs


# ----------------------------------------------------------------- geometry

def circle_path(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return "M%s,%s a%s,%s 0 1 0 %s,0 a%s,%s 0 1 0 %s,0 Z" % (
        fmt(cx - r), fmt(cy), fmt(r), fmt(r), fmt(2 * r), fmt(r), fmt(r), fmt(-2 * r))


def ellipse_path(cx, cy, rx, ry):
    cx, cy, rx, ry = float(cx), float(cy), float(rx), float(ry)
    return "M%s,%s a%s,%s 0 1 0 %s,0 a%s,%s 0 1 0 %s,0 Z" % (
        fmt(cx - rx), fmt(cy), fmt(rx), fmt(ry), fmt(2 * rx), fmt(rx), fmt(ry), fmt(-2 * rx))


def rect_path(x, y, w, h, rx=0, ry=None):
    x, y, w, h, rx = float(x), float(y), float(w), float(h), float(rx)
    ry = rx if ry is None else float(ry)
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    if rx <= 0 or ry <= 0:
        return "M%s,%s h%s v%s h%s Z" % (fmt(x), fmt(y), fmt(w), fmt(h), fmt(-w))
    return ("M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%s h%s "
            "a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%s Z") % (
        fmt(x + rx), fmt(y), fmt(w - 2 * rx),
        fmt(rx), fmt(ry), fmt(rx), fmt(ry), fmt(h - 2 * ry),
        fmt(rx), fmt(ry), fmt(-rx), fmt(ry), fmt(-(w - 2 * rx)),
        fmt(rx), fmt(ry), fmt(-rx), fmt(-ry), fmt(-(h - 2 * ry)),
        fmt(rx), fmt(ry), fmt(rx), fmt(-ry))


# ---------------------------------------------------------------- gradients

def parse_gradients(root):
    grads = {}
    for el in root.iter():
        tag = local(el.tag)
        if tag not in ("linearGradient", "radialGradient"):
            continue
        a = style_attrs(el)
        gid = a.get("id")
        if not gid:
            raise ValueError("gradient without id")
        if a.get("gradientUnits", "objectBoundingBox") != "userSpaceOnUse":
            raise ValueError('gradient %s: only gradientUnits="userSpaceOnUse" is supported' % gid)
        if "gradientTransform" in a:
            raise ValueError("gradient %s: gradientTransform is not supported" % gid)
        if a.get("href") or a.get("{%s}href" % XLINK_NS):
            raise ValueError("gradient %s: href inheritance is not supported" % gid)
        stops = []
        for st in el:
            if local(st.tag) != "stop":
                continue
            sa = style_attrs(st)
            off = sa.get("offset", "0").strip()
            off = float(off[:-1]) / 100 if off.endswith("%") else float(off)
            col = parse_color(sa.get("stop-color", "#000000"))
            op = float(sa.get("stop-opacity", "1"))
            stops.append((off, col, op))
        if len(stops) < 2:
            raise ValueError("gradient %s needs at least two stops" % gid)
        if tag == "linearGradient":
            g = {"type": "linear",
                 "startX": a.get("x1", "0"), "startY": a.get("y1", "0"),
                 "endX": a.get("x2", "0"), "endY": a.get("y2", "0")}
        else:
            if "fx" in a or "fy" in a:
                raise ValueError("gradient %s: fx/fy focal points are not supported" % gid)
            g = {"type": "radial",
                 "centerX": a.get("cx", "0"), "centerY": a.get("cy", "0"),
                 "gradientRadius": a.get("r", "0")}
        g["stops"] = stops
        grads[gid] = g
    return grads


# ----------------------------------------------------------------- elements

def convert_shape(el, grads, inherited_opacity, out, indent):
    tag = local(el.tag)
    a = style_attrs(el)

    if tag == "path":
        d = a["d"].strip()
    elif tag == "circle":
        d = circle_path(a["cx"], a["cy"], a["r"])
    elif tag == "ellipse":
        d = ellipse_path(a["cx"], a["cy"], a["rx"], a["ry"])
    elif tag == "rect":
        d = rect_path(a.get("x", 0), a.get("y", 0), a["width"], a["height"],
                      a.get("rx", a.get("ry", 0)), a.get("ry"))
    else:
        raise ValueError("unsupported element <%s>" % tag)

    for bad in ("transform", "stroke", "filter", "mask", "clip-path"):
        if bad in a and a[bad] not in ("none",):
            raise ValueError("<%s> attribute %s is not supported (convert to filled shapes)" % (tag, bad))
    d = re.sub(r"\s+", " ", d)

    fill = a.get("fill", "#000000").strip()
    opacity = inherited_opacity * float(a.get("fill-opacity", 1)) * float(a.get("opacity", 1))
    fill_type = ""
    if a.get("fill-rule") == "evenodd":
        fill_type = ' android:fillType="evenOdd"'

    if fill == "none":
        return
    pad = " " * indent
    m = re.fullmatch(r"url\(#([^)]+)\)", fill)
    if m:
        g = grads.get(m.group(1))
        if g is None:
            raise ValueError("unknown gradient %r" % m.group(1))
        out.append('%s<path%s\n%s    android:pathData="%s">' % (pad, fill_type, pad, d))
        out.append('%s    <aapt:attr name="android:fillColor">' % pad)
        if g["type"] == "linear":
            out.append('%s        <gradient android:type="linear" android:startX="%s" android:startY="%s" '
                       'android:endX="%s" android:endY="%s">' % (
                           pad, fmt(g["startX"]), fmt(g["startY"]), fmt(g["endX"]), fmt(g["endY"])))
        else:
            out.append('%s        <gradient android:type="radial" android:centerX="%s" android:centerY="%s" '
                       'android:gradientRadius="%s">' % (
                           pad, fmt(g["centerX"]), fmt(g["centerY"]), fmt(g["gradientRadius"])))
        for off, col, op in g["stops"]:
            out.append('%s            <item android:offset="%s" android:color="%s"/>' % (
                pad, fmt(off), argb(col, op * opacity)))
        out.append('%s        </gradient>' % pad)
        out.append('%s    </aapt:attr>' % pad)
        out.append('%s</path>' % pad)
    else:
        out.append('%s<path%s\n%s    android:fillColor="%s"\n%s    android:pathData="%s"/>' % (
            pad, fill_type, pad, argb(parse_color(fill), opacity), pad, d))


def convert_children(parent, grads, inherited_opacity, out, indent):
    for el in parent:
        tag = local(el.tag)
        if tag in ("defs", "title", "desc", "metadata") or not isinstance(el.tag, str):
            continue
        if tag == "g":
            a = style_attrs(el)
            if "transform" in a:
                raise ValueError("<g transform> is not supported - bake the transform into coordinates")
            op = inherited_opacity * float(a.get("opacity", 1)) * float(a.get("fill-opacity", 1))
            name = a.get("id")
            pad = " " * indent
            out.append('%s<group%s>' % (pad, (' android:name="%s"' % name) if name else ""))
            convert_children(el, grads, op, out, indent + 4)
            out.append('%s</group>' % pad)
        else:
            convert_shape(el, grads, inherited_opacity, out, indent)


def convert(svg_text):
    root = ET.fromstring(svg_text)
    if local(root.tag) != "svg":
        raise ValueError("root element is not <svg>")
    vb = root.attrib.get("viewBox")
    if not vb:
        raise ValueError("<svg> needs a viewBox")
    minx, miny, vw, vh = [float(v) for v in re.split(r"[\s,]+", vb.strip())]
    if minx or miny:
        raise ValueError("viewBox must start at 0 0")
    grads = parse_gradients(root)
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by art/spot/svg2vd.py - edit the SVG in art/spot/ instead. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="%sdp"' % fmt(vw),
           '    android:height="%sdp"' % fmt(vh),
           '    android:viewportWidth="%s"' % fmt(vw),
           '    android:viewportHeight="%s">' % fmt(vh)]
    convert_children(root, grads, 1.0, out, 4)
    out.append('</vector>')
    return "\n".join(out) + "\n"


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("inputs", nargs="+", help="input .svg files (or in.svg out.xml)")
    ap.add_argument("-o", "--out-dir", help="output directory for <name>.xml")
    args = ap.parse_args(argv)

    pairs = []
    if args.out_dir:
        for src in args.inputs:
            name = os.path.splitext(os.path.basename(src))[0] + ".xml"
            pairs.append((src, os.path.join(args.out_dir, name)))
    elif len(args.inputs) == 2 and args.inputs[1].endswith(".xml"):
        pairs.append(tuple(args.inputs))
    else:
        ap.error("give -o OUT_DIR or exactly 'in.svg out.xml'")

    for src, dst in pairs:
        with open(src, encoding="utf-8") as f:
            xml = convert(f.read())
        os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
        with open(dst, "w", encoding="utf-8") as f:
            f.write(xml)
        print("%s -> %s" % (src, dst))


if __name__ == "__main__":
    main(sys.argv[1:])
