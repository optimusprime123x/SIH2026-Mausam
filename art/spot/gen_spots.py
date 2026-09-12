#!/usr/bin/env python3
"""gen_spots.py - generates the spot_*.svg sources in this directory (240x200 spot
illustrations for Mausam Home).  Edit the scene functions at the bottom, then run:

  python3 art/spot/gen_spots.py            # rewrite all SVGs (or pass names)
  python3 art/spot/svg2vd.py art/spot/*.svg -o app/src/main/res/drawable/

Everything is emitted in the tiny subset understood by art/spot/svg2vd.py:
path / circle / ellipse / rect, userSpaceOnUse gradients, no transforms,
no strokes.  Rotations are baked into coordinates.

Screen coordinates (y down): angle a -> (cx + r cos a, cy + r sin a);
increasing angle == clockwise on screen == SVG sweep-flag 1.
"""
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

# palette
SKY, DEEP, INDIGO, CORAL, AMBER, TEAL = "#4CC2FF", "#0078D4", "#6B7BFF", "#FF7A59", "#FFC83D", "#29D3C3"
VIOLET, MINT, ROSE, WHITE, SHADE, INK = "#A78BFA", "#7CE0A9", "#FF6B9A", "#FFFFFF", "#DCE6F2", "#1B2440"


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def P(x, y):
    return "%s,%s" % (f(x), f(y))


def rot(p, c, deg):
    if not deg:
        return p
    a = math.radians(deg)
    dx, dy = p[0] - c[0], p[1] - c[1]
    return (c[0] + dx * math.cos(a) - dy * math.sin(a), c[1] + dx * math.sin(a) + dy * math.cos(a))


def polar(c, r, deg):
    a = math.radians(deg)
    return (c[0] + r * math.cos(a), c[1] + r * math.sin(a))


# ------------------------------------------------------------------ emitters

def el(tag, **kw):
    attrs = []
    for k, v in kw.items():
        if v is None:
            continue
        k = k.replace("_", "-")
        if isinstance(v, float):
            v = f(v)
        attrs.append('%s="%s"' % (k, v))
    return "  <%s %s/>" % (tag, " ".join(attrs))


def path(d, fill, op=None, rule=None):
    return el("path", fill=fill, fill_opacity=op, fill_rule=rule, d=d)


def circle(cx, cy, r, fill, op=None):
    return el("circle", cx=float(cx), cy=float(cy), r=float(r), fill=fill, fill_opacity=op)


def ellipse(cx, cy, rx, ry, fill, op=None):
    return el("ellipse", cx=float(cx), cy=float(cy), rx=float(rx), ry=float(ry), fill=fill, fill_opacity=op)


def lin(gid, p1, p2, stops):
    s = "".join('\n      <stop offset="%s" stop-color="%s"%s/>' % (
        f(o), c, (' stop-opacity="%s"' % f(a)) if a is not None else "") for o, c, a in
        [(st[0], st[1], st[2] if len(st) > 2 else None) for st in stops])
    return '    <linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s\n    </linearGradient>' % (
        gid, f(p1[0]), f(p1[1]), f(p2[0]), f(p2[1]), s)


def rad(gid, c, r, stops):
    s = "".join('\n      <stop offset="%s" stop-color="%s"%s/>' % (
        f(o), col, (' stop-opacity="%s"' % f(a)) if a is not None else "") for o, col, a in
        [(st[0], st[1], st[2] if len(st) > 2 else None) for st in stops])
    return '    <radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s\n    </radialGradient>' % (
        gid, f(c[0]), f(c[1]), f(r), s)


def svg(comment, defs, body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 200">\n'
            '  <!-- %s\n       Generated geometry: only path/circle/ellipse/rect + userSpaceOnUse gradients, no transforms, no strokes. -->\n'
            '  <defs>\n%s\n  </defs>\n%s\n</svg>\n') % (comment, "\n".join(defs), "\n".join(body))


# ------------------------------------------------------------------ geometry

def capsule(p1, p2, w):
    """Filled line with round caps from p1 to p2, thickness w."""
    r = w / 2
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    L = math.hypot(dx, dy)
    nx, ny = -dy / L * r, dx / L * r
    A = (p1[0] + nx, p1[1] + ny); B = (p2[0] + nx, p2[1] + ny)
    C = (p2[0] - nx, p2[1] - ny); D = (p1[0] - nx, p1[1] - ny)
    return "M%s L%s A%s,%s 0 0 0 %s L%s A%s,%s 0 0 0 %s Z" % (P(*A), P(*B), f(r), f(r), P(*C), P(*D), f(r), f(r), P(*A))


def rrect(cx, cy, w, h, r, deg=0, pivot=None):
    """Rounded rect centred on (cx,cy), optionally rotated by deg around pivot (default centre)."""
    pivot = pivot or (cx, cy)
    x0, y0 = cx - w / 2, cy - h / 2
    r = min(r, w / 2, h / 2)
    pts = [("M", (x0 + r, y0)), ("L", (x0 + w - r, y0)), ("A", (x0 + w, y0 + r)),
           ("L", (x0 + w, y0 + h - r)), ("A", (x0 + w - r, y0 + h)),
           ("L", (x0 + r, y0 + h)), ("A", (x0, y0 + h - r)),
           ("L", (x0, y0 + r)), ("A", (x0 + r, y0))]
    out = []
    for cmd, p in pts:
        p = rot(p, pivot, deg)
        out.append(("A%s,%s 0 0 1 %s" % (f(r), f(r), P(*p))) if cmd == "A" else ("%s%s" % (cmd, P(*p))))
    return " ".join(out) + " Z"


def ring(c, r1, r2, a1, a2):
    """Annular sector r1<r2 between angles a1<a2 (deg), round ends."""
    large = 1 if (a2 - a1) > 180 else 0
    rm = (r2 - r1) / 2
    p1, p2, p3, p4 = polar(c, r2, a1), polar(c, r2, a2), polar(c, r1, a2), polar(c, r1, a1)
    return ("M%s A%s,%s 0 %d 1 %s A%s,%s 0 0 1 %s A%s,%s 0 %d 0 %s A%s,%s 0 0 1 %s Z" % (
        P(*p1), f(r2), f(r2), large, P(*p2), f(rm), f(rm), P(*p3), f(r1), f(r1), large, P(*p4), f(rm), f(rm), P(*p1)))


def crescent(c, r, a1, a2, r_in):
    """Region between the circle arc a1->a2 (clockwise, through the middle) and a flatter
    arc of radius r_in through the same end points.  Used for highlights / shading."""
    p1, p2 = polar(c, r, a1), polar(c, r, a2)
    large = 1 if (a2 - a1) > 180 else 0
    return "M%s A%s,%s 0 %d 1 %s A%s,%s 0 0 0 %s Z" % (P(*p1), f(r), f(r), large, P(*p2), f(r_in), f(r_in), P(*p1))


def annulus(c, r_out, r_in):
    def circ(r):
        return "M%s A%s,%s 0 1 0 %s A%s,%s 0 1 0 %s Z" % (P(c[0] - r, c[1]), f(r), f(r), P(c[0] + r, c[1]), f(r), f(r), P(c[0] - r, c[1]))
    return circ(r_out) + " " + circ(r_in)


def star4(c, r, k=0.22, deg=0):
    """Four-point sparkle with concave sides."""
    pts = [polar(c, r, -90 + deg), polar(c, r, 0 + deg), polar(c, r, 90 + deg), polar(c, r, 180 + deg)]
    ctrl = [polar(c, r * k * 1.414, -45 + deg), polar(c, r * k * 1.414, 45 + deg),
            polar(c, r * k * 1.414, 135 + deg), polar(c, r * k * 1.414, 225 + deg)]
    d = "M" + P(*pts[0])
    for i in range(4):
        d += " Q%s %s" % (P(*ctrl[i]), P(*pts[(i + 1) % 4]))
    return d + " Z"


def drop(c, r, h, deg=0, pivot=None):
    """Teardrop: round bottom radius r centred at c, tip h above the centre."""
    pivot = pivot or c
    cx, cy = c
    k = 0.55 * r
    pts = [("M", (cx, cy - h)),
           ("C", (cx + 0.35 * r, cy - h + 0.45 * h), (cx + r, cy - k), (cx + r, cy)),
           ("A", (cx - r, cy)),
           ("C", (cx - r, cy - k), (cx - 0.35 * r, cy - h + 0.45 * h), (cx, cy - h))]
    out = []
    for seg in pts:
        cmd, ps = seg[0], [rot(p, pivot, deg) for p in seg[1:]]
        if cmd == "A":
            out.append("A%s,%s 0 1 1 %s" % (f(r), f(r), P(*ps[0])))
        else:
            out.append(cmd + " ".join(P(*p) for p in ps))
    return " ".join(out) + " Z"


def lens(p1, p2, bulge):
    """Leaf / lens between p1 and p2; bulge = sagitta ratio (0.5 = semicircles)."""
    c = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
    s = bulge * c
    r = (s * s + (c / 2) ** 2) / (2 * s)
    return "M%s A%s,%s 0 0 1 %s A%s,%s 0 0 1 %s Z" % (P(*p1), f(r), f(r), P(*p2), f(r), f(r), P(*p1))


def circ_isect_upper(c1, c2):
    (x1, y1, r1), (x2, y2, r2) = c1, c2
    d = math.hypot(x2 - x1, y2 - y1)
    assert abs(r1 - r2) < d < r1 + r2, "circles must intersect: %r %r" % (c1, c2)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(r1 * r1 - a * a, 0))
    mx, my = x1 + a * (x2 - x1) / d, y1 + a * (y2 - y1) / d
    ux, uy = -(y2 - y1) / d, (x2 - x1) / d
    p_a = (mx + h * ux, my + h * uy)
    p_b = (mx - h * ux, my - h * uy)
    return p_a if p_a[1] < p_b[1] else p_b


def ang(c, p):
    return math.degrees(math.atan2(p[1] - c[1], p[0] - c[0])) % 360


def cloud(x0, x1, base_y, rc, bumps):
    """Single-path cloud: flat bottom from x0..x1 (round caps radius rc) + bump circles
    (cx, cy, r) listed left -> right.  Outline traced counter-clockwise over the top."""
    circles = [(x0 + rc, base_y - rc, rc)] + list(bumps) + [(x1 - rc, base_y - rc, rc)]
    n = len(circles)
    isect = [circ_isect_upper(circles[i], circles[i + 1]) for i in range(n - 1)]
    d = "M%s L%s" % (P(x0 + rc, base_y), P(x1 - rc, base_y))
    # walk circles right -> left, each arc with decreasing angle (sweep 0)
    for i in range(n - 1, -1, -1):
        cx, cy, r = circles[i]
        a_start = 90 if i == n - 1 else ang((cx, cy), isect[i])
        end_pt = (x0 + rc, base_y) if i == 0 else isect[i - 1]
        a_end = ang((cx, cy), end_pt)
        ext = (a_start - a_end) % 360
        d += " A%s,%s 0 %d 0 %s" % (f(r), f(r), 1 if ext > 180 else 0, P(*end_pt))
    return d + " Z"


def blob(c, rx, ry, jitter, seed_rot=0):
    """Organic closed blob through 8 jittered points on an ellipse (Catmull-Rom -> cubic)."""
    n = len(jitter)
    pts = []
    for i in range(n):
        a = math.radians(seed_rot + 360 * i / n)
        j = jitter[i]
        pts.append((c[0] + rx * j * math.cos(a), c[1] + ry * j * math.sin(a)))
    d = "M" + P(*pts[0])
    for i in range(n):
        p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += " C%s %s %s" % (P(*c1), P(*c2), P(*p2))
    return d + " Z"


def ribbon(p0, p1, p2, p3, w0, w1, n=16):
    """Tapered filled stroke along a cubic (width w0 at start -> w1 at end)."""
    def bez(t):
        u = 1 - t
        return (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])

    def dbez(t):
        u = 1 - t
        return (3 * u * u * (p1[0] - p0[0]) + 6 * u * t * (p2[0] - p1[0]) + 3 * t * t * (p3[0] - p2[0]),
                3 * u * u * (p1[1] - p0[1]) + 6 * u * t * (p2[1] - p1[1]) + 3 * t * t * (p3[1] - p2[1]))
    left, right = [], []
    for i in range(n + 1):
        t = i / n
        x, y = bez(t); dx, dy = dbez(t)
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        w = (w0 + (w1 - w0) * t) / 2
        left.append((x + nx * w, y + ny * w)); right.append((x - nx * w, y - ny * w))
    pts = left + right[::-1]
    # smooth through the sampled points with Catmull-Rom
    d = "M" + P(*pts[0])
    m = len(pts)
    for i in range(m):
        q0, q1, q2, q3 = pts[(i - 1) % m], pts[i], pts[(i + 1) % m], pts[(i + 2) % m]
        c1 = (q1[0] + (q2[0] - q0[0]) / 6, q1[1] + (q2[1] - q0[1]) / 6)
        c2 = (q2[0] - (q3[0] - q1[0]) / 6, q2[1] - (q3[1] - q1[1]) / 6)
        d += " C%s %s %s" % (P(*c1), P(*c2), P(*q2))
    return d + " Z"


# ------------------------------------------------------------------ motifs

_SH = [0]


def contact_shadow(defs, cx, cy, r, n=1, spacing=0.0, color=DEEP, op=0.3):
    """Soft blurred-looking spot(s): circles filled with a radial gradient fading to 0.
    Appends the gradient defs; returns the shapes."""
    out = []
    for i in range(n):
        x = cx + (i - (n - 1) / 2) * spacing
        _SH[0] += 1
        gid = "sh%d" % _SH[0]
        defs.append(rad(gid, (x, cy), r, [(0, color, op), (0.55, color, op * 0.4), (1, color, 0)]))
        out.append(circle(x, cy, r, "url(#%s)" % gid))
    return out


def drop_shadow(d, dx=3, dy=5, color=DEEP, op=0.16):
    return path(shift_d(d, dx, dy), color, op)


def shift_d(d, dx, dy):
    """Shift absolute path data by (dx,dy).  Only M/L/C/Q/A absolute + Z are used here."""
    import re
    toks = re.findall(r"[MLCQAZ]|-?[\d.]+", d)
    out, i = [], 0
    cmd = None
    while i < len(toks):
        t = toks[i]
        if t in "MLCQAZ":
            cmd = t; out.append(t); i += 1
            continue
        if cmd == "A":
            nums = toks[i:i + 7]
            out.append("%s,%s %s %s %s %s" % (nums[0], nums[1], nums[2], nums[3], nums[4], P(float(nums[5]) + dx, float(nums[6]) + dy)))
            i += 7
        else:
            out.append(P(float(toks[i]) + dx, float(toks[i + 1]) + dy)); i += 2
    return " ".join(out).replace("M ", "M").replace("L ", "L").replace("C ", "C").replace("Q ", "Q").replace("A ", "A")


def sun(c, r, ray_in, ray_out, ray_w, gid, glow=True, n=8, offset=0, highlight=True):
    out = []
    if glow:
        out.append(circle(c[0], c[1], ray_out + 4, AMBER, 0.18))
    for i in range(n):
        a = offset + 360 * i / n
        out.append(path(capsule(polar(c, ray_in, a), polar(c, ray_out, a), ray_w), AMBER))
    out.append(circle(c[0], c[1], r, "url(#%s)" % gid))
    if highlight:
        out.append(path(crescent(c, r - 1.5, 185, 285, (r - 1.5) * 1.35), WHITE, 0.35))
    return out


def sun_defs(gid, c, r):
    return lin(gid, (c[0] - r, c[1] - r), (c[0] + r, c[1] + r), [(0, "#FFE38F"), (1, "#FFBE2E")])


def cloud_motif(x0, x1, base_y, rc, bumps, gid, shadow=True, dx=3, dy=5):
    d = cloud(x0, x1, base_y, rc, bumps)
    out = []
    if shadow:
        out.append(drop_shadow(d, dx, dy))
    out.append(path(d, "url(#%s)" % gid))
    return out


def cloud_defs(gid, x0, x1, top, base_y, tint="#DCE6F2"):
    return lin(gid, (x0, top), (x1, base_y), [(0, WHITE), (0.55, WHITE), (1, tint)])


def tick(c, size, w, color=WHITE):
    """Check mark: short stroke down-right, long stroke up-right, joined by a disc."""
    a = (c[0] - size * 0.55, c[1] - size * 0.05)
    b = (c[0] - size * 0.12, c[1] + size * 0.38)
    e = (c[0] + size * 0.62, c[1] - size * 0.42)
    return [path(capsule(a, b, w), color), path(capsule(b, e, w), color), circle(b[0], b[1], w / 2, color)]


def plus(c, size, w, color=WHITE):
    return [path(capsule((c[0] - size, c[1]), (c[0] + size, c[1]), w), color),
            path(capsule((c[0], c[1] - size), (c[0], c[1] + size), w), color)]


def mini_sun(c, r, color=AMBER, deg=0):
    out = [circle(c[0], c[1], r, color)]
    for i in range(8):
        a = deg + 45 * i
        out.append(path(capsule(polar(c, r * 1.45, a), polar(c, r * 2.05, a), r * 0.42), color))
    return out


def leaf(c, L, deg, color, vein=WHITE):
    p1 = rot((c[0] - L / 2, c[1] + L / 2), c, deg)
    p2 = rot((c[0] + L / 2, c[1] - L / 2), c, deg)
    d = lens(p1, p2, 0.32)
    v1 = rot((c[0] - L * 0.3, c[1] + L * 0.3), c, deg)
    v2 = rot((c[0] + L * 0.3, c[1] - L * 0.3), c, deg)
    return [path(d, color), path(capsule(v1, v2, L * 0.09), vein, 0.85)]


def bell(c, h, color=WHITE):
    """Bell centred on c with overall height h (dome + lip + clapper)."""
    cx, cy = c
    r = h * 0.36          # dome radius
    top = cy - h * 0.5 + 2
    lip_y = cy + h * 0.2
    d = ("M%s A%s,%s 0 0 1 %s C%s %s %s L%s C%s %s %s Z" % (
        P(cx - r, top + r), f(r), f(r), P(cx + r, top + r),
        P(cx + r, top + r + h * 0.22), P(cx + r * 1.05, lip_y - h * 0.12), P(cx + r * 1.3, lip_y),
        P(cx - r * 1.3, lip_y),
        P(cx - r * 1.05, lip_y - h * 0.12), P(cx - r, top + r + h * 0.22), P(cx - r, top + r)))
    return [path(d, color),
            path(rrect(cx, lip_y, r * 2.9, h * 0.13, h * 0.065), color),
            circle(cx, lip_y + h * 0.16, h * 0.11, color),
            circle(cx, top - 1.2, h * 0.07, color)]


def sparkles(items):
    out = []
    for it in items:
        if it[0] == "star":
            _, c, r, col = it
            out.append(path(star4(c, r), col))
        else:
            _, c, r, col = it
            out.append(circle(c[0], c[1], r, col))
    return out


# ------------------------------------------------------------------ pin

def pin_path(c, r, tip):
    """Map pin: head circle (c, r) + tail to tip; tangent tail."""
    cx, cy = c
    tx, ty = tip
    # tangent points ~ +/-38 deg below horizontal
    a = 38
    pl, pr = polar(c, r, 180 - a), polar(c, r, a)
    large = 1
    d = "M%s C%s %s %s A%s,%s 0 %d 1 %s C%s %s %s Z" % (
        P(tx, ty), P(tx - r * 0.42, ty - (ty - pl[1]) * 0.45), P(pl[0] - r * 0.04, pl[1] + r * 0.32), P(*pl),
        f(r), f(r), large, P(*pr),
        P(pr[0] + r * 0.04, pr[1] + r * 0.32), P(tx + r * 0.42, ty - (ty - pr[1]) * 0.45), P(tx, ty))
    return d


def pin_motif(c, r, tip, gid, hole=0.4):
    cx, cy = c
    out = [path(pin_path(c, r, tip), "url(#%s)" % gid)]
    # right-side shading, top-left inner highlight
    out.append(path(crescent(c, r, 318, 408, r * 1.7), "#00437A", 0.16))
    out.append(path(crescent(c, r - 1.2, 165, 275, (r - 1.2) * 1.4), WHITE, 0.26))
    # hole
    hr = r * hole
    out.append(circle(cx, cy, hr + r * 0.09, "#00437A", 0.28))
    out.append(circle(cx, cy, hr, WHITE))
    out.append(path(crescent(c, hr, 195, 345, hr * 1.6), INK, 0.09))
    # specular
    out.append(circle(cx - r * 0.55, cy - r * 0.5, r * 0.16, WHITE, 0.6))
    out.append(circle(cx - r * 0.28, cy - r * 0.76, r * 0.08, WHITE, 0.6))
    return out


# ================================================================== scenes

def spot_location():
    defs = [
        lin("pin", (88, 50), (150, 140), [(0, "#5BC8FF"), (1, "#0078D4")]),
        lin("ground", (30, 150), (200, 190), [(0, "#A3ECC4"), (1, "#7CE0A9")]),
        lin("hillBack", (40, 118), (170, 168), [(0, "#7FE5D3"), (1, "#3ED6C4")]),
        lin("hillFront", (110, 132), (220, 168), [(0, "#9BEBBD"), (1, "#5ED69A")]),
        sun_defs("sun", (186, 50), 17),
        cloud_defs("cloudA", 146, 206, 46, 84),
        cloud_defs("cloudB", 32, 68, 42, 67),
    ]
    b = []
    b.append(path(blob((122, 108), 100, 80, [1.0, 0.94, 1.0, 0.98, 1.0, 0.92, 1.0, 0.97], 10), SKY, 0.2))
    b.append(path(blob((198, 46), 42, 36, [1.0, 0.95, 1.0, 0.9, 1.0, 0.96, 0.95, 1.0], 30), AMBER, 0.14))
    # sun + clouds
    b += sun((186, 50), 17, 22, 30, 4.4, "sun", offset=22.5)
    b += cloud_motif(146, 206, 84, 7, [(163, 71, 12), (183, 63, 17), (198, 73, 11)], "cloudA")
    b += cloud_motif(32, 68, 67, 4.5, [(42, 58, 7), (54, 52, 10), (63, 59, 6.5)], "cloudB", dx=2, dy=3)
    # ground: base ellipse, back hill (teal), front hill (mint), path
    b.append(ellipse(120, 166, 96, 24, "url(#ground)"))
    b.append(path("M28,166 C40,120 96,110 138,140 C154,152 162,160 174,166 Z", "url(#hillBack)"))
    b.append(path("M112,166 C126,128 178,124 206,148 C212,154 214,160 216,166 Z", "url(#hillFront)"))
    b.append(path(ribbon((122, 153), (138, 158), (146, 172), (170, 181), 3, 7), WHITE, 0.55))
    # tiny trees
    for (tx, ty, s, col) in [(60, 148, 5.5, "#1FB9AC"), (70, 154, 4, "#29D3C3"), (198, 150, 4.6, "#3FC98A")]:
        b.append(path(capsule((tx, ty + s), (tx, ty + s * 1.9), s * 0.5), "#0D8F86" if col != "#3FC98A" else "#2FA86F"))
        b.append(circle(tx, ty, s, col))
        b.append(circle(tx - s * 0.32, ty - s * 0.32, s * 0.3, WHITE, 0.35))
    # pin
    b += contact_shadow(defs, 121, 151, 24, op=0.38)
    b.append('  <g id="pin">')
    b += ["  " + s for s in pin_motif((120, 84), 36, (120, 150), "pin")]
    b.append("  </g>")
    b += sparkles([("dot", (78, 34), 3, ROSE), ("dot", (214, 112), 2.5, SKY), ("dot", (26, 124), 2.2, AMBER),
                   ("star", (98, 28), 5, AMBER)])
    return svg("Onboarding 'allow location': premium map pin over two hill tones with a tiny path; sun behind a cloud. Light from top-left.", defs, b)


def spot_personas():
    pivot = (120, 214)
    cards = [  # (angle, gradient stops, icon)
        (-21, ("#B9A4FF", "#6B7BFF"), "sun"),
        (21, ("#FFA78F", "#FF6F4E"), "leaf"),
        (0, ("#5BC8FF", "#0078D4"), "drop"),
    ]
    defs, b = [], []
    b.append(path(blob((120, 104), 100, 82, [1.0, 0.95, 1.0, 0.96, 1.0, 0.93, 1.0, 0.97], 20), VIOLET, 0.22))
    b.append(path(blob((36, 150), 34, 30, [1.0, 0.9, 1.0, 0.96, 0.95, 1.0, 0.92, 1.0], 40), SKY, 0.18))
    b.append(path(blob((204, 48), 32, 28, [1.0, 0.95, 0.9, 1.0, 0.96, 1.0, 0.92, 1.0], 0), TEAL, 0.18))
    W, H, R = 56, 80, 15
    for idx, (deg, (c0, c1), icon) in enumerate(cards):
        cx, cy = (120, 100) if deg == 0 else (120, 108)
        gid = "card%d" % idx
        tl = rot((cx - W / 2, cy - H / 2), pivot, deg)
        br = rot((cx + W / 2, cy + H / 2), pivot, deg)
        defs.append(lin(gid, tl, br, [(0, c0), (1, c1)]))
        d = rrect(cx, cy, W, H, R, deg, pivot)
        b.append(drop_shadow(d, 3, 5, DEEP, 0.18))
        b.append(path(d, "url(#%s)" % gid))
        # inner top-left sheen
        # icon disc
        ic = rot((cx, cy - 20), pivot, deg)
        b.append(circle(ic[0], ic[1], 13, WHITE, 0.92))
        if icon == "sun":
            b += mini_sun(ic, 4.6, AMBER, deg)
        elif icon == "drop":
            b.append(path(drop((ic[0], ic[1] + 2.4), 5.2, 12.5, deg, pivot=ic), DEEP))
            b.append(circle(ic[0] - 2, ic[1] + 3.2, 1.5, WHITE, 0.7))
        else:
            b += leaf(ic, 15, deg + 6, TEAL)
        # text bars
        for (w, dy, op) in [(36, 8, 0.8), (24, 20, 0.55)]:
            bc = rot((cx - 18 + w / 2, cy + dy), pivot, deg)
            b.append(path(rrect(bc[0], bc[1], w, 6, 3, deg, bc), WHITE, op))
    b += sparkles([("star", (36, 50), 8, AMBER), ("star", (214, 142), 6, ROSE), ("dot", (200, 60), 3, SKY),
                   ("dot", (30, 150), 2.5, TEAL), ("dot", (150, 182), 2.2, VIOLET), ("dot", (120, 34), 2.4, CORAL)])
    return svg("Onboarding 'what matters to you': three fanned app cards (violet / blue / coral) each with an icon disc and two text bars. Light from top-left.", defs, b)


def spot_all_clear():
    sc = (96, 84)
    defs = [sun_defs("sun", sc, 36),
            lin("shield", (146, 104), (206, 182), [(0, "#8BE8B6"), (1, "#25C9B8")]),
            cloud_defs("cloud", 34, 96, 108, 148)]
    b = []
    b.append(path(blob((118, 102), 100, 82, [1.0, 0.95, 1.0, 0.97, 1.0, 0.92, 1.0, 0.96], 5), SKY, 0.2))
    b.append(path(blob((186, 146), 46, 40, [1.0, 0.93, 1.0, 0.95, 1.0, 0.94, 0.97, 1.0], 15), MINT, 0.24))
    # sun with 8 long + 8 short rays
    b.append(circle(sc[0], sc[1], 58, AMBER, 0.14))
    for i in range(8):
        b.append(path(capsule(polar(sc, 45, 45 * i), polar(sc, 57, 45 * i), 6), AMBER))
        b.append(path(capsule(polar(sc, 45, 45 * i + 22.5), polar(sc, 51, 45 * i + 22.5), 4.2), AMBER, 0.85))
    b.append(circle(sc[0], sc[1], 36, "url(#sun)"))
    b.append(path(crescent(sc, 34.5, 185, 285, 34.5 * 1.35), WHITE, 0.32))
    b.append(circle(sc[0] - 16, sc[1] - 14, 6.5, WHITE, 0.5))
    b.append(circle(sc[0] - 4, sc[1] - 25, 3.2, WHITE, 0.5))
    # cloud tucked at bottom-left of sun
    b += cloud_motif(34, 96, 148, 7, [(50, 138, 11), (68, 128, 16), (85, 138, 10)], "cloud")
    # shield
    b += contact_shadow(defs, 176, 186, 16, n=3, spacing=14, op=0.26)
    outer = "M176,92 C189,100 200,104 214,106 L214,140 C214,164 197,179 176,188 C155,179 138,164 138,140 L138,106 C152,104 163,100 176,92 Z"
    inner = "M176,98.5 C188,105 198,109 208.5,111 L208.5,139 C208.5,160 194,173 176,181 C158,173 143.5,160 143.5,139 L143.5,111 C154,109 164,105 176,98.5 Z"
    b.append(path(outer, WHITE))
    b.append(path(inner, "url(#shield)"))
    b.append(path("M176,98.5 C164,105 154,109 143.5,111 L143.5,139 C143.5,150 147,159 153,166 C160,150 168,122 176,98.5 Z", WHITE, 0.12))
    b += tick((176, 140), 30, 7)
    b += sparkles([("star", (190, 44), 9, TEAL), ("star", (40, 58), 6, ROSE), ("star", (212, 82), 4.5, AMBER),
                   ("dot", (30, 112), 2.4, MINT), ("dot", (122, 176), 2.6, SKY)])
    return svg("'All clear, no warnings': friendly sun, small cloud, mint shield with a tick and sparkles. Light from top-left.", defs, b)


def spot_destinations():
    defs = [lin("wing", (112, 30), (156, 56), [(0, "#FFC5B3"), (1, "#FFA089")]),
            lin("body", (118, 44), (162, 66), [(0, "#FF8F70"), (1, "#FF6F4E")]),
            sun_defs("sunR", (208, 118), 10),
            cloud_defs("cloudL", 30, 78, 130, 158), cloud_defs("cloudR", 178, 222, 128, 150),
            lin("islandL", (24, 156), (86, 172), [(0, "#9BEBBD"), (1, "#5ED69A")]),
            lin("islandR", (168, 150), (226, 166), [(0, "#9BEBBD"), (1, "#5ED69A")])]
    b = []
    b.append(path(blob((120, 106), 100, 80, [1.0, 0.94, 1.0, 0.97, 1.0, 0.93, 1.0, 0.96], 0), INDIGO, 0.2))
    # dotted arch at equal arc-length spacing, skipping the plane region
    p0, p1, p2, p3 = (56, 126), (68, 34), (172, 34), (192, 116)

    def bez(t):
        u = 1 - t
        return (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])
    samples = [bez(i / 400) for i in range(401)]
    acc, last, dots = 0, samples[0], []
    for q in samples[1:]:
        acc += math.hypot(q[0] - last[0], q[1] - last[1]); last = q
        if acc >= 11.5:
            acc = 0; dots.append(q)
    for q in dots:
        if 100 < q[0] < 166:
            continue
        b.append(circle(q[0], q[1], 2.4, INDIGO, 0.9))
    # left island: skyline + cloud
    b.append(ellipse(56, 164, 32, 7, "url(#islandL)"))
    for (x, w, h, col) in [(38, 12, 30, "#6B7BFF"), (52, 14, 44, "#0078D4"), (68, 12, 34, "#4CC2FF")]:
        b.append(path(rrect(x + w / 2, 162 - h / 2, w, h, 3), col))
        for wy in range(int(162 - h + 7), 156, 8):
            b.append(circle(x + w / 2 - 3, wy, 1.3, WHITE, 0.8))
            b.append(circle(x + w / 2 + 3, wy, 1.3, WHITE, 0.8))
    b += cloud_motif(30, 78, 158, 6, [(41, 150, 9), (55, 144, 13), (69, 151, 8)], "cloudL", dx=2, dy=3)
    # right island: sun + skyline + cloud
    b.append(ellipse(200, 158, 30, 7, "url(#islandR)"))
    b += sun((208, 118), 10, 13.5, 19, 3.2, "sunR", offset=22.5)
    for (x, w, h, col) in [(180, 12, 28, "#4CC2FF"), (194, 14, 38, "#6B7BFF"), (210, 12, 24, "#0078D4")]:
        b.append(path(rrect(x + w / 2, 156 - h / 2, w, h, 3), col))
        for wy in range(int(156 - h + 7), 150, 8):
            b.append(circle(x + w / 2 - 3, wy, 1.3, WHITE, 0.8))
            b.append(circle(x + w / 2 + 3, wy, 1.3, WHITE, 0.8))
    b += cloud_motif(178, 222, 150, 5, [(188, 143, 8), (200, 137, 11.5), (213, 144, 7)], "cloudR", dx=2, dy=3)
    # paper plane at the apex, heading right, tilted 12 deg down
    N = (152, 50)
    deg = 12

    def T(p):
        return rot((N[0] + p[0], N[1] + p[1]), N, deg)
    nose, tailTop, fold, tailBot, mid = T((0, 0)), T((-44, -20)), T((-26, 0)), T((-36, 14)), T((-33, -7))
    poly = lambda *ps: "M" + " L".join(P(*p) for p in ps) + " Z"
    b.append(path(poly(nose, tailTop, fold), "url(#wing)"))
    b.append(path(poly(nose, fold, tailBot), "url(#body)"))
    b.append(path(poly(nose, mid, fold), INK, 0.18))
    b.append(path(poly(nose, tailTop, mid), WHITE, 0.22))
    # motion trail
    for (x0, x1, y, op) in [(-64, -52, -8, 0.55), (-76, -52, 1, 0.8), (-66, -54, 10, 0.55)]:
        b.append(path(capsule(T((x0, y)), T((x1, y)), 3), SKY, op))
    b += sparkles([("star", (34, 66), 7, AMBER), ("star", (206, 60), 5.5, ROSE), ("dot", (126, 168), 3, TEAL),
                   ("dot", (98, 24), 2.6, VIOLET), ("dot", (188, 26), 2.2, SKY), ("dot", (24, 116), 2.2, CORAL)])
    return svg("'Add cities': crisp paper plane on a dotted arc between two tiny city islands. Light from top-left.", defs, b)


def spot_offline():
    defs = [lin("cloud", (70, 60), (176, 140), [(0, "#F7F4FF"), (0.5, "#EEE9FF"), (1, "#D5CCF6")]),
            lin("slash", (100, 82), (140, 122), [(0, "#FF8FB3"), (1, "#FF5F92")])]
    b = []
    b.append(path(blob((122, 108), 100, 80, [1.0, 0.95, 1.0, 0.94, 1.0, 0.96, 1.0, 0.93], 25), VIOLET, 0.22))
    b.append(path(blob((196, 160), 40, 34, [1.0, 0.92, 1.0, 0.95, 0.94, 1.0, 0.96, 1.0], 10), INDIGO, 0.18))
    cd = cloud(58, 182, 138, 14, [(86, 108, 26), (122, 90, 36), (158, 112, 22)])
    b.append(path(shift_d(cd, 4, 6), INDIGO, 0.28))
    b.append(path(cd, "url(#cloud)"))
    b.append(path(crescent((122, 90), 34.5, 190, 280, 34.5 * 1.4), WHITE, 0.6))
    b.append(path(crescent((86, 108), 24.5, 190, 260, 24.5 * 1.5), WHITE, 0.5))
    # no-signal mark
    c = (120, 120)
    b.append(circle(c[0], c[1], 6, INDIGO))
    for (r1, r2, op) in [(12.5, 18, 1.0), (23, 28.5, 0.78), (33.5, 39, 0.56)]:
        b.append(path(ring(c, r1, r2, 225, 315), INDIGO, op))
    b.append(path(capsule((94, 80), (146, 132), 15), "url(#cloud)"))
    b.append(path(capsule((96, 82), (144, 130), 6.5), "url(#slash)"))
    b += sparkles([("star", (188, 36), 7, VIOLET), ("dot", (48, 66), 3, VIOLET), ("dot", (198, 66), 2.6, INDIGO),
                   ("dot", (38, 146), 2.2, SKY), ("dot", (178, 164), 2.4, VIOLET)])
    return svg("'Offline / sample data': soft lavender cloud with a muted no-signal mark (three arcs, slashed). Light from top-left.", defs, b)


def spot_empty_cards():
    defs = [lin("cardBack", (60, 40), (170, 120), [(0, "#C7B5FF"), (1, "#A78BFA")]),
            lin("cardMid", (62, 56), (172, 130), [(0, "#94A0FF"), (1, "#6B7BFF")]),
            lin("thumb", (76, 84), (108, 112), [(0, "#5BC8FF"), (1, "#0078D4")]),
            lin("badge", (158, 64), (186, 94), [(0, "#8BE8B6"), (1, "#25C9B8")]),
            lin("front", (64, 72), (176, 146), [(0, WHITE), (0.6, WHITE), (1, "#EAF0F8")])]
    b = []
    b.append(path(blob((122, 106), 100, 80, [1.0, 0.95, 1.0, 0.94, 1.0, 0.96, 1.0, 0.93], 15), SKY, 0.2))
    b.append(path(blob((40, 140), 36, 32, [1.0, 0.92, 1.0, 0.95, 0.94, 1.0, 0.96, 1.0], 30), VIOLET, 0.2))
    W, H, R = 112, 72, 16
    back = rrect(116, 84, W, H, R, -7)
    mid = rrect(118, 96, W, H, R, -3.5)
    front = rrect(120, 110, W, H, R, 0)
    b.append(drop_shadow(back, 3, 5, DEEP, 0.14)); b.append(path(back, "url(#cardBack)"))
    b.append(drop_shadow(mid, 3, 5, DEEP, 0.16)); b.append(path(mid, "url(#cardMid)"))
    b.append(drop_shadow(front, 3, 6, DEEP, 0.2)); b.append(path(front, "url(#front)"))
    # front content: thumbnail with sun + cloud, text bars, chip
    b.append(path(rrect(93, 96, 30, 28, 8), "url(#thumb)"))
    b.append(circle(89, 92, 5.5, AMBER))
    b.append(path(cloud(83, 105, 105, 3.5, [(89, 101, 4.5), (96, 98, 6), (101, 102, 4)]), WHITE))
    b.append(path(rrect(139, 89, 46, 7, 3.5), "#D5DFEC"))
    b.append(path(rrect(133, 102, 34, 7, 3.5), "#E2E9F3"))
    b.append(path(rrect(112, 122, 68, 7, 3.5), "#E2E9F3"))
    b.append(path(rrect(98, 135, 40, 7, 3.5), SKY, 0.6))
    # badge
    b += contact_shadow(defs, 174, 82, 26, op=0.2)
    b.append(circle(172, 78, 19, WHITE))
    b.append(circle(172, 78, 15.5, "url(#badge)"))
    b.append(path(crescent((172, 78), 15, 185, 285, 15 * 1.4), WHITE, 0.28))
    b += plus((172, 78), 7, 4.4)
    b += sparkles([("star", (44, 58), 7, AMBER), ("star", (204, 122), 5.5, ROSE), ("dot", (200, 44), 3, TEAL),
                   ("dot", (40, 122), 2.6, CORAL), ("dot", (150, 172), 2.4, VIOLET)])
    return svg("'No cards yet': stack of three rounded cards, the front one with content and a mint plus badge. Light from top-left.", defs, b)


def spot_brief():
    defs = [lin("phone", (54, 40), (142, 180), [(0, "#5BC8FF"), (1, "#0062B8")]),
            lin("screen", (60, 48), (136, 172), [(0, "#FFFFFF"), (1, "#E4ECF7")]),
            lin("card", (100, 60), (206, 112), [(0, WHITE), (0.6, WHITE), (1, "#EAF0F8")]),
            lin("bellDisc", (118, 74), (146, 102), [(0, "#FFDC7A"), (1, "#FFB93A")]),
            sun_defs("sun", (200, 52), 15),
            cloud_defs("cloud", 14, 56, 46, 70),
            lin("miniCard", (68, 98), (128, 130), [(0, "#5BC8FF"), (1, "#0078D4")])]
    b = []
    b.append(path(blob((118, 108), 100, 80, [1.0, 0.94, 1.0, 0.97, 1.0, 0.93, 1.0, 0.96], 35), SKY, 0.2))
    b.append(path(blob((198, 60), 40, 36, [1.0, 0.95, 1.0, 0.9, 1.0, 0.96, 0.95, 1.0], 30), AMBER, 0.14))
    b += sun((200, 52), 15, 20, 27, 4.2, "sun", offset=22.5)
    b += cloud_motif(14, 56, 70, 5, [(25, 62, 8), (39, 55, 11), (50, 62, 7)], "cloud", dx=2, dy=3)
    # phone
    phone = rrect(98, 112, 92, 144, 20)
    b.append(drop_shadow(phone, 4, 6, DEEP, 0.2))
    b.append(path(phone, "url(#phone)"))
    b.append(path(rrect(98, 112, 80, 128, 13), "url(#screen)"))
    b.append(path(rrect(98, 56, 22, 5, 2.5), "#0062B8", 0.55))       # camera pill
    # screen skeleton: greeting bars, mini weather card, list rows
    b.append(path(rrect(78, 74, 24, 6, 3), "#D5DFEC"))
    b.append(path(rrect(88, 85, 44, 5, 2.5), "#E2E9F3"))
    b.append(path(rrect(98, 114, 60, 32, 10), "url(#miniCard)"))
    b.append(circle(82, 108, 6, AMBER))
    b.append(path(cloud(76, 100, 118, 3.5, [(82, 114, 4.5), (89, 111, 6), (95, 115, 4)]), WHITE))
    b.append(path(rrect(110, 108, 22, 6, 3), WHITE, 0.85))
    b.append(path(rrect(107, 119, 16, 5, 2.5), WHITE, 0.55))
    for y in (144, 160):
        b.append(circle(76, y, 5.5, "#D5DFEC"))
        b.append(path(rrect(105, y - 3, 38, 5, 2.5), "#E2E9F3"))
        b.append(path(rrect(99, y + 5, 26, 4, 2), "#EAF0F8"))
    # notification card popping out (tilted)
    deg = -6
    cc = (150, 84)
    card = rrect(cc[0], cc[1], 104, 48, 14, deg)
    b.append(drop_shadow(card, 3, 7, DEEP, 0.2))
    b.append(path(card, "url(#card)"))
    disc = rot((cc[0] - 32, cc[1]), cc, deg)
    b.append(circle(disc[0], disc[1], 14, "url(#bellDisc)"))
    b += bell(disc, 17)
    b.append(circle(disc[0] + 10, disc[1] - 9.5, 4.2, WHITE))
    b.append(circle(disc[0] + 10, disc[1] - 9.5, 3, CORAL))
    for (w, dy, col) in [(46, -7, "#C9D6E8"), (32, 6, "#DCE6F2")]:
        bc = rot((cc[0] - 12 + w / 2, cc[1] + dy), cc, deg)
        b.append(path(rrect(bc[0], bc[1], w, 6.5, 3.25, deg, bc), col))
    b += sparkles([("star", (36, 118), 7, AMBER), ("star", (206, 132), 6, ROSE), ("dot", (214, 96), 2.6, SKY),
                   ("dot", (28, 160), 2.4, TEAL), ("dot", (160, 176), 2.4, VIOLET), ("dot", (82, 30), 2.4, CORAL)])
    return svg("'Daily briefs': phone with a friendly notification card (bell + sun) popping out. Light from top-left.", defs, b)


def spot_search():
    lc = (118, 92)
    defs = [lin("rim", (lc[0] - 50, lc[1] - 50), (lc[0] + 50, lc[1] + 50), [(0, WHITE), (0.55, WHITE), (1, "#D5DFEC")]),
            lin("glass", (lc[0] - 44, lc[1] - 44), (lc[0] + 44, lc[1] + 44), [(0, WHITE, 0.3), (1, SKY, 0.14)]),
            lin("handle", (158, 128), (200, 170), [(0, "#4CC2FF"), (1, "#0062B8")]),
            lin("pin", (100, 66), (132, 118), [(0, "#FF9E86"), (1, "#FF6640")]),
            cloud_defs("cloud", 52, 116, 108, 150)]
    b = []
    b.append(path(blob((118, 106), 100, 80, [1.0, 0.95, 1.0, 0.94, 1.0, 0.96, 1.0, 0.93], 45), SKY, 0.2))
    b.append(path(blob((44, 152), 38, 34, [1.0, 0.92, 1.0, 0.95, 0.94, 1.0, 0.96, 1.0], 20), TEAL, 0.18))
    # cloud behind the lens (bottom-left)
    b += cloud_motif(52, 116, 150, 7, [(68, 140, 11), (86, 130, 16), (103, 140, 10)], "cloud")
    # pin under the lens
    b += contact_shadow(defs, 112, 126, 12, op=0.26)
    b.append('  <g id="pin">')
    b += ["  " + s for s in pin_motif((112, 88), 18, (112, 126), "pin", hole=0.38)]
    b.append("  </g>")
    # handle then lens
    b += contact_shadow(defs, 194, 176, 14, n=2, spacing=12, op=0.22)
    hd = capsule((156, 130), (194, 168), 17)
    b.append(path(shift_d(hd, 3, 5), DEEP, 0.18))
    b.append(path(hd, "url(#handle)"))
    b.append(path(capsule((160, 134), (172, 146), 5), WHITE, 0.3))
    b.append(path(shift_d(annulus(lc, 52, 43), 3, 6), DEEP, 0.16, "evenodd"))
    b.append(circle(lc[0], lc[1], 44, "url(#glass)"))
    b.append(path(crescent(lc, 41, 195, 275, 41 * 1.25), WHITE, 0.55))
    b.append(path(crescent(lc, 41, 20, 80, 41 * 1.5), WHITE, 0.26))
    b.append(path(annulus(lc, 52, 43), "url(#rim)", None, "evenodd"))
    b.append(path(crescent(lc, 51.5, 185, 280, 51.5 * 1.2), WHITE, 0.5))
    b += sparkles([("star", (40, 56), 8, AMBER), ("star", (200, 60), 6, ROSE), ("dot", (206, 110), 2.6, TEAL),
                   ("dot", (30, 112), 2.4, VIOLET), ("dot", (142, 182), 2.4, SKY), ("dot", (176, 30), 2.4, CORAL)])
    return svg("'No results': magnifier over a coral map pin and a cloud. Light from top-left.", defs, b)


SCENES = {
    "spot_location": spot_location,
    "spot_personas": spot_personas,
    "spot_all_clear": spot_all_clear,
    "spot_destinations": spot_destinations,
    "spot_offline": spot_offline,
    "spot_empty_cards": spot_empty_cards,
    "spot_brief": spot_brief,
    "spot_search": spot_search,
}

if __name__ == "__main__":
    import sys
    names = sys.argv[1:] or list(SCENES)
    os.makedirs(OUT, exist_ok=True)
    for n in names:
        with open(os.path.join(OUT, n + ".svg"), "w") as fh:
            fh.write(SCENES[n]())
        print("wrote", n)
