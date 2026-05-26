from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.platypus import Image as RLImage
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from PIL import Image as PILImage, ImageDraw
from io import BytesIO
import math

# ── Canvas helpers ─────────────────────────────────────────────────────────
UPLOADS = "/root/.claire/uploads/"   # unused placeholder; real uploads below
IMG_W = 1.8 * cm
IMG_H = 1.8 * cm
SZ = 120   # icon canvas pixels

# Icon color palette (matching PDF scheme)
IC_BG   = (232, 240, 248)
IC_MAIN = (15,  52,  96)
IC_RED  = (233, 69,  96)
IC_GRAY = (175, 190, 205)
IC_WHT  = (255, 255, 255)
IC_LIT  = (210, 228, 248)
IC_GOLD = (210, 190,  60)


def _bake(im):
    buf = BytesIO()
    im.save(buf, format="JPEG", quality=88)
    buf.seek(0)
    return RLImage(buf, width=IMG_W, height=IMG_H)


def _canvas():
    im = PILImage.new("RGB", (SZ, SZ), IC_BG)
    return im, ImageDraw.Draw(im)


# ── Icon library ───────────────────────────────────────────────────────────

def icon_motor():
    """Brushless motor — top-down view."""
    im, d = _canvas()
    d.ellipse([8, 8, 112, 112], outline=IC_MAIN, width=5)
    d.ellipse([32, 32, 88, 88], outline=IC_MAIN, width=3)
    for i in range(8):
        a = math.radians(i * 45)
        d.line([(60 + 33*math.cos(a), 60 + 33*math.sin(a)),
                (60 + 46*math.cos(a), 60 + 46*math.sin(a))], fill=IC_MAIN, width=3)
    for i in range(4):
        d.arc([10, 10, 110, 110], start=i*90+5, end=i*90+85, fill=IC_RED, width=7)
    d.ellipse([50, 50, 70, 70], fill=IC_MAIN)
    return _bake(im)


def icon_antenna():
    """5.8 GHz helical VTX antenna."""
    im, d = _canvas()
    d.rectangle([52, 88, 68, 114], fill=IC_MAIN)
    d.rounded_rectangle([43, 18, 77, 92], radius=9, fill=IC_LIT, outline=IC_MAIN, width=2)
    y = 24
    for i in range(10):
        x1 = 43 if i % 2 == 0 else 77
        x2 = 77 if i % 2 == 0 else 43
        d.line([(x1, y), (x2, y + 7)], fill=IC_MAIN, width=2)
        y += 7
    d.ellipse([43, 13, 77, 22], fill=IC_MAIN)
    return _bake(im)


def icon_rx_diversity():
    """BETAFPV SuperD diversity receiver."""
    im, d = _canvas()
    d.rounded_rectangle([18, 46, 102, 84], radius=5,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.line([(30, 46), (15, 8)],  fill=IC_MAIN, width=3)
    d.ellipse([9, 3, 21, 15],  fill=IC_RED)
    d.line([(90, 46), (105, 8)], fill=IC_MAIN, width=3)
    d.ellipse([99, 3, 111, 15], fill=IC_RED)
    d.rectangle([50, 80, 70, 96], fill=IC_MAIN)
    for x in [30, 46, 62, 78, 90]:
        d.rounded_rectangle([x-4, 56, x+4, 74], radius=2, fill=IC_MAIN)
    return _bake(im)


def icon_tx_radio():
    """Radiomaster TX16S remote controller."""
    im, d = _canvas()
    d.rounded_rectangle([5, 22, 115, 106], radius=14,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.rounded_rectangle([32, 28, 88, 50], radius=3, fill=IC_MAIN)
    # Left gimbal
    d.ellipse([12, 56, 50, 94], outline=IC_MAIN, width=2, fill=IC_WHT)
    d.ellipse([24, 68, 38, 82], fill=IC_MAIN)
    # Right gimbal
    d.ellipse([70, 56, 108, 94], outline=IC_MAIN, width=2, fill=IC_WHT)
    d.ellipse([82, 68, 96, 82], fill=IC_MAIN)
    # Antennas
    d.line([(32, 22), (22, 3)],  fill=IC_MAIN, width=4)
    d.line([(88, 22), (98, 3)],  fill=IC_MAIN, width=4)
    d.ellipse([19, 0, 26, 7], fill=IC_RED)
    d.ellipse([95, 0, 102, 7], fill=IC_RED)
    return _bake(im)


def icon_charger():
    """ISDT Q8 Max 1000W charger."""
    im, d = _canvas()
    d.rounded_rectangle([6, 16, 114, 104], radius=8,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.rounded_rectangle([13, 23, 78, 62], radius=3, fill=IC_MAIN)
    for i, h in enumerate([28, 38, 22, 36, 30, 25]):
        x = 17 + i * 9
        d.rectangle([x, 62 - h//3, x+6, 60], fill=IC_WHT)
    for y in [35, 50, 65]:
        d.ellipse([84, y, 100, y + 13], fill=IC_MAIN)
    for x in [18, 38, 58, 78]:
        d.rectangle([x, 97, x + 14, 108], fill=IC_MAIN)
    return _bake(im)


def icon_dc_psu():
    """DC 30V 30A bench power supply."""
    im, d = _canvas()
    d.rectangle([5, 18, 115, 102], fill=IC_LIT, outline=IC_MAIN, width=3)
    for i in range(3):
        for j in range(3):
            d.ellipse([16+i*15, 26+j*15, 28+i*15, 38+j*15],
                      outline=IC_MAIN, width=1)
    d.ellipse([66, 28, 104, 66], outline=IC_MAIN, width=3, fill=IC_WHT)
    d.ellipse([78, 40, 92, 54], fill=IC_MAIN)
    d.line([(85, 28), (85, 36)], fill=IC_MAIN, width=3)
    d.ellipse([70, 74, 86, 90], fill=IC_RED)
    d.ellipse([90, 74, 106, 90], fill=IC_MAIN)
    d.rounded_rectangle([16, 72, 50, 86], radius=4, fill=IC_MAIN)
    return _bake(im)


def icon_connector():
    """XT90 / XT60 connector pair."""
    im, d = _canvas()
    d.rounded_rectangle([20, 30, 100, 90], radius=10,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.ellipse([28, 44, 56, 72], fill=IC_RED, outline=IC_MAIN, width=2)
    d.ellipse([64, 44, 92, 72], fill=IC_MAIN)
    d.line([(42, 90), (42, 112)], fill=IC_RED, width=5)
    d.line([(78, 90), (78, 112)], fill=(20, 20, 20), width=5)
    d.rectangle([48, 30, 72, 36], fill=IC_MAIN)
    return _bake(im)


def icon_wire():
    """Silicone wire — red + black coil."""
    im, d = _canvas()
    for r, col in [(40, IC_RED), (22, (20, 20, 20))]:
        pts = [(60 + r*math.cos(math.radians(t)),
                60 + r*math.sin(math.radians(t))) for t in range(0, 375, 25)]
        for i in range(len(pts)-1):
            d.line([pts[i], pts[i+1]], fill=col, width=5)
    return _bake(im)


def icon_capacitor():
    """50V 1000µF electrolytic capacitor."""
    im, d = _canvas()
    d.rounded_rectangle([33, 20, 87, 86], radius=5, fill=IC_MAIN)
    d.ellipse([33, 14, 87, 28], fill=IC_MAIN)
    d.ellipse([33, 78, 87, 92], fill=IC_MAIN)
    d.rectangle([67, 20, 83, 86], fill=IC_LIT)
    d.line([(50, 92), (50, 112)], fill=IC_MAIN, width=4)
    d.line([(70, 92), (70, 112)], fill=IC_MAIN, width=4)
    d.line([(50,  8), (66,  8)], fill=IC_MAIN, width=2)
    d.line([(58,  0), (58, 16)], fill=IC_MAIN, width=2)
    return _bake(im)


def icon_heatshrink():
    """Heat shrink tubing set."""
    im, d = _canvas()
    bands = [(12, 14, 108, 42), (12, 46, 108, 74), (12, 78, 108, 106)]
    cols  = [IC_RED, (50, 120, 200), IC_MAIN]
    for (x1,y1,x2,y2), c in zip(bands, cols):
        d.rounded_rectangle([x1, y1, x2, y2], radius=9, fill=c, outline=IC_MAIN, width=1)
        d.line([(x1+14, y1+7), (x2-14, y1+7)], fill=IC_WHT, width=1)
    return _bake(im)


def icon_velcro():
    """250 mm velcro battery strap."""
    im, d = _canvas()
    d.rounded_rectangle([6, 42, 114, 78], radius=12, fill=IC_MAIN, outline=IC_MAIN, width=2)
    d.rounded_rectangle([44, 36, 76, 84], radius=7, fill=IC_LIT, outline=IC_MAIN, width=3)
    d.line([(60, 36), (60, 84)], fill=IC_MAIN, width=2)
    for x in range(10, 42, 8):
        for y in range(48, 72, 8):
            d.rectangle([x, y, x+4, y+4], fill=IC_LIT)
    for x in range(78, 112, 8):
        for y in range(48, 72, 8):
            d.rectangle([x, y, x+4, y+4], fill=IC_LIT)
    return _bake(im)


def icon_solder():
    """63/37 rosin-core solder reel."""
    im, d = _canvas()
    d.ellipse([6, 6, 114, 114], fill=IC_LIT, outline=IC_MAIN, width=4)
    d.ellipse([28, 28, 92, 92], fill=IC_MAIN)
    d.ellipse([42, 42, 78, 78], fill=IC_LIT)
    d.ellipse([50, 50, 70, 70], fill=IC_MAIN)
    for r in [20, 25, 30, 35]:
        d.arc([60-r, 60-r, 60+r, 60+r], start=25, end=315, fill=IC_GOLD, width=3)
    return _bake(im)


def icon_hex_driver():
    """1.5 / 2 / 2.5 / 3 mm hex driver set."""
    im, d = _canvas()
    for i, (xb, w) in enumerate([(22, 6), (48, 5), (74, 4)]):
        d.line([(xb, 96), (xb, 28)], fill=IC_MAIN, width=w)
        d.line([(xb, 28), (xb+18, 28)], fill=IC_MAIN, width=w)
        d.ellipse([xb+13, 22, xb+24, 34], fill=IC_RED)
    return _bake(im)


def icon_frame_15():
    """15-inch X-frame top view."""
    im, d = _canvas()
    cx, cy = 60, 60
    for a in [45, 135, 225, 315]:
        r = math.radians(a)
        d.line([(cx, cy),
                (cx + 48*math.cos(r), cy + 48*math.sin(r))], fill=IC_MAIN, width=7)
    d.rounded_rectangle([44, 44, 76, 76], radius=6, fill=IC_MAIN)
    for a in [45, 135, 225, 315]:
        r = math.radians(a)
        mx, my = cx + 48*math.cos(r), cy + 48*math.sin(r)
        d.ellipse([mx-12, my-12, mx+12, my+12], fill=IC_RED, outline=IC_MAIN, width=2)
        d.ellipse([mx-5,  my-5,  mx+5,  my+5],  fill=IC_WHT)
    return _bake(im)


def icon_prop_15():
    """15-inch 3-blade propeller."""
    im, d = _canvas()
    cx, cy = 60, 60
    for base_a in [90, 210, 330]:
        pts = []
        for t in range(42):
            a = math.radians(base_a + t * 0.65)
            r = 9 + t * 1.15
            bw = (42 - t) * 0.16
            pts.append((cx + r*math.cos(a) - bw*math.sin(a),
                        cy + r*math.sin(a) + bw*math.cos(a)))
        for t in range(41, -1, -1):
            a = math.radians(base_a + t * 0.65)
            r = 9 + t * 1.15
            bw = (42 - t) * 0.16
            pts.append((cx + r*math.cos(a) + bw*math.sin(a),
                        cy + r*math.sin(a) - bw*math.cos(a)))
        if len(pts) >= 3:
            d.polygon(pts, fill=IC_MAIN)
    d.ellipse([53, 53, 67, 67], fill=IC_RED)
    return _bake(im)


def icon_hard_case():
    """Battery protective hard case."""
    im, d = _canvas()
    d.rounded_rectangle([6, 28, 114, 106], radius=9,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.line([(6, 67), (114, 67)], fill=IC_MAIN, width=3)
    for x in [26, 74]:
        d.rounded_rectangle([x, 60, x+18, 74], radius=3, fill=IC_MAIN)
        d.ellipse([x+4, 63, x+14, 71], fill=IC_LIT)
    d.arc([38, 14, 82, 36], start=180, end=0, fill=IC_MAIN, width=6)
    d.rounded_rectangle([14, 36, 106, 64], radius=5,
                        fill=IC_WHT, outline=IC_GRAY, width=1)
    return _bake(im)


def icon_fiber_module():
    """15KM fiber optic sky/ground module."""
    im, d = _canvas()
    d.rounded_rectangle([6, 22, 84, 98], radius=8,
                        fill=IC_LIT, outline=IC_MAIN, width=3)
    d.rectangle([13, 30, 50, 58], fill=IC_MAIN)
    d.ellipse([55, 30, 76, 51], fill=IC_RED)
    d.rectangle([13, 64, 36, 78], fill=IC_MAIN)
    d.rectangle([44, 64, 76, 78], fill=IC_MAIN)
    for i in range(5):
        d.line([(84, 60-i*5), (116, 60-i*10)], fill=(80, 160, 255), width=2)
    d.line([(84, 60), (118, 60)], fill=(80, 160, 255), width=3)
    return _bake(im)


def icon_fiber_cable():
    """Single-mode LC/APC fiber patch cable."""
    im, d = _canvas()
    d.rounded_rectangle([4, 48, 28, 72], radius=4, fill=IC_MAIN)
    d.rectangle([8, 53, 24, 67], fill=IC_LIT)
    d.rounded_rectangle([92, 48, 116, 72], radius=4, fill=IC_MAIN)
    d.rectangle([96, 53, 112, 67], fill=IC_LIT)
    pts = [(28, 60)]
    for t in range(1, 21):
        pts.append((28 + t*3.2, 60 + 18*math.sin(math.pi*t/10)))
    pts.append((92, 60))
    for i in range(len(pts)-1):
        d.line([pts[i], pts[i+1]], fill=(80, 200, 255), width=4)
    return _bake(im)


def icon_fiber_sleeve():
    """Fiber optic protective sleeve."""
    im, d = _canvas()
    d.rounded_rectangle([8, 20, 112, 54], radius=14, fill=IC_MAIN, outline=IC_MAIN, width=2)
    d.ellipse([ 8, 22,  38, 52], fill=IC_LIT)
    d.ellipse([82, 22, 112, 52], fill=IC_LIT)
    for x in range(42, 80, 10):
        d.line([(x, 20), (x, 54)], fill=IC_LIT, width=1)
    d.line([(8, 37), (112, 37)], fill=(80, 200, 255), width=3)
    d.rounded_rectangle([8, 62, 112, 96], radius=10,
                        fill=IC_LIT, outline=IC_MAIN, width=2)
    for x in range(22, 108, 14):
        d.line([(x, 62), (x, 96)], fill=IC_GRAY, width=1)
    return _bake(im)


# ── Photo-based image specs: (path, crop_px or None) ──────────────────────
_UP = "/root/.claude/uploads/fe593dea-afe8-45a3-a7c0-ffe46d5eddbc/"

CAMERA   = (_UP + "7ca46921-1000600883.jpg",  None)
MOTOR_15 = (_UP + "66c7838c-1000601847.jpg",  (580, 0, 1550, 904))
VTX      = (_UP + "a6004eb7-1000600890.jpg",  (30, 60, 480, 420))
RX_ELRS  = (_UP + "e49fa772-1000600893.jpg",  (0, 50, 500, 520))
BAT_8S   = (_UP + "b1fce124-1000600668.jpg",  (50, 100, 720, 900))
BAT_6S   = (_UP + "cd573ff2-1000600709.jpg",  (50, 200, 650, 1000))
FRAME_10 = (_UP + "9cb1854b-1000601602.jpg",  (400, 0, 3600, 3000))
FCESC    = (_UP + "73ee8b32-1000601609.jpg",  (1300, 0, 2800, 1560))
PROP_HQ  = (_UP + "2dff912b-1000600888.jpg",  (250, 50, 500, 530))
PROP_GF  = (_UP + "8f510c44-1000600887.jpg",  (0, 50, 250, 450))

# Pre-rendered icon constants
MOTOR_ICON   = icon_motor()
ANTENNA_ICON = icon_antenna()
RX_DIV_ICON  = icon_rx_diversity()
TX16S_ICON   = icon_tx_radio()
CHARGER_ICON = icon_charger()
PSU_ICON     = icon_dc_psu()
CONN_ICON    = icon_connector()
WIRE_ICON    = icon_wire()
CAP_ICON     = icon_capacitor()
HEAT_ICON    = icon_heatshrink()
VELCRO_ICON  = icon_velcro()
SOLDER_ICON  = icon_solder()
HEX_ICON     = icon_hex_driver()
FRAME15_ICON = icon_frame_15()
PROP15_ICON  = icon_prop_15()
CASE_ICON    = icon_hard_case()
FIBER_ICON   = icon_fiber_module()
FCBL_ICON    = icon_fiber_cable()
FSLV_ICON    = icon_fiber_sleeve()


def make_img_cell(path, crop_px=None):
    try:
        im = PILImage.open(path).convert("RGB")
        if crop_px:
            im = im.crop(crop_px)
        im.thumbnail((SZ, SZ), PILImage.LANCZOS)
        canvas = PILImage.new("RGB", (SZ, SZ), IC_WHT)
        ox = (SZ - im.size[0]) // 2
        oy = (SZ - im.size[1]) // 2
        canvas.paste(im, (ox, oy))
        buf = BytesIO()
        canvas.save(buf, format="JPEG", quality=88)
        buf.seek(0)
        return RLImage(buf, width=IMG_W, height=IMG_H)
    except Exception:
        return ""


def img(spec):
    if spec is None:
        return ""
    if isinstance(spec, RLImage):
        return spec
    path, crop = spec
    return make_img_cell(path, crop)


# ── PDF document setup ─────────────────────────────────────────────────────
doc = SimpleDocTemplate(
    "/home/user/jdjv/parts-list.pdf",
    pagesize=A4,
    rightMargin=1.5*cm, leftMargin=1.5*cm,
    topMargin=1.5*cm,   bottomMargin=1.5*cm,
)
W, H = A4

DARK   = colors.HexColor("#1a1a2e")
ACCENT = colors.HexColor("#0f3460")
LIGHT  = colors.HexColor("#e94560")
GRAY   = colors.HexColor("#f0f0f0")
WHITE  = colors.white

def header_style(size=13):
    return ParagraphStyle("hdr", fontName="Helvetica-Bold", fontSize=size,
                          textColor=WHITE, alignment=TA_CENTER, leading=size+4)

def sub_style(size=9):
    return ParagraphStyle("sub", fontName="Helvetica", fontSize=size,
                          textColor=DARK, alignment=TA_LEFT, leading=size+3)

def section_style():
    return ParagraphStyle("sec", fontName="Helvetica-Bold", fontSize=11,
                          textColor=WHITE, alignment=TA_LEFT, leading=15)

elements = []

# ── Title block ────────────────────────────────────────────────────────────
title_table = Table(
    [[Paragraph("QUAD BUILD — PARTS LIST", header_style(16)),
      Paragraph("10-inch  &amp;  15-inch", header_style(10))]],
    colWidths=[12*cm, 5.5*cm]
)
title_table.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), DARK),
    ("ROWPADDING", (0,0), (-1,-1), 10),
    ("ALIGN",     (0,0), (-1,-1), "CENTER"),
    ("VALIGN",    (0,0), (-1,-1), "MIDDLE"),
    ("ROUNDEDCORNERS", [6,6,6,6]),
]))
elements.append(title_table)
elements.append(Spacer(1, 0.4*cm))

st = Table([[
    Paragraph("Phase 1: Full Radio + FPV Build",               sub_style(9)),
    Paragraph("Phase 2: Fiber Optic Upgrade (swap VTX only)",  sub_style(9)),
]], colWidths=[8.5*cm, 9*cm])
st.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), GRAY),
    ("ROWPADDING", (0,0), (-1,-1), 6),
    ("GRID",       (0,0), (-1,-1), 0.5, colors.HexColor("#cccccc")),
]))
elements.append(st)
elements.append(Spacer(1, 0.5*cm))

# Column widths: [photo, #, name, qty]  — total 17.5 cm
cw = [2.0*cm, 0.8*cm, 11.2*cm, 3.5*cm]

_CH = {"fontName": "Helvetica-Bold", "fontSize": 9, "textColor": DARK}


def _col_header(bg):
    t = Table([[
        Paragraph("",                  ParagraphStyle("ph", **_CH, alignment=TA_CENTER)),
        Paragraph("#",                 ParagraphStyle("ch", **_CH, alignment=TA_CENTER)),
        Paragraph("Part / Component",  ParagraphStyle("c2", **_CH, alignment=TA_LEFT)),
        Paragraph("Qty",               ParagraphStyle("c3", **_CH, alignment=TA_CENTER)),
    ]], colWidths=cw)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), bg),
        ("ROWPADDING", (0,0), (-1,-1), 5),
        ("GRID",       (0,0), (-1,-1), 0.3, colors.HexColor("#aaaaaa")),
    ]))
    return t


def make_section(title_text, rows, hdr_color=None, qty_color=None):
    """rows: list of (name, qty, img_spec) triples."""
    if hdr_color is None:
        hdr_color = ACCENT
    if qty_color is None:
        qty_color = ACCENT
    col_bg = colors.HexColor("#dce8f5") if hdr_color == ACCENT else colors.HexColor("#fde8ec")
    section = []

    hdr = Table([[Paragraph(title_text, section_style())]], colWidths=[sum(cw)])
    hdr.setStyle(TableStyle([
        ("BACKGROUND",  (0,0), (-1,-1), hdr_color),
        ("ROWPADDING",  (0,0), (-1,-1), 7),
        ("LEFTPADDING", (0,0), (-1,-1), 10),
    ]))
    section.append(hdr)
    section.append(_col_header(col_bg))

    for i, (name, qty, img_spec) in enumerate(rows):
        bg = WHITE if i % 2 == 0 else GRAY
        row = Table([[
            img(img_spec),
            Paragraph(str(i+1), ParagraphStyle(
                "n", fontName="Helvetica", fontSize=8,
                textColor=colors.HexColor("#666666"), alignment=TA_CENTER)),
            Paragraph(name, ParagraphStyle(
                "nm", fontName="Helvetica", fontSize=8.5,
                textColor=DARK, alignment=TA_LEFT)),
            Paragraph(qty, ParagraphStyle(
                "q", fontName="Helvetica-Bold", fontSize=8.5,
                textColor=qty_color, alignment=TA_CENTER)),
        ]], colWidths=cw)
        row.setStyle(TableStyle([
            ("BACKGROUND",  (0,0), (-1,-1), bg),
            ("ROWPADDING",  (0,0), (-1,-1), 4),
            ("GRID",        (0,0), (-1,-1), 0.2, colors.HexColor("#cccccc")),
            ("LEFTPADDING", (2,0), (2,0),   8),
            ("VALIGN",      (0,0), (-1,-1), "MIDDLE"),
            ("ALIGN",       (0,0), (0,-1),  "CENTER"),
        ]))
        section.append(row)

    section.append(Spacer(1, 0.4*cm))
    return section


# ── Section 1: 10-inch Quad ────────────────────────────────────────────────
ten_phase1 = [
    ("Frame — Mark4 V2 10-inch Carbon Fiber",                              "×2",     FRAME_10),
    ("Motor — Arcosky 3115 900KV  (4 per drone)",                          "×8",     MOTOR_ICON),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (invoice)",          "×5",     MOTOR_ICON),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (additional)",       "×4",     MOTOR_ICON),
    ("FC / ESC Stack — F722 F405 55A All-in-One",                          "×2",     FCESC),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens",                        "×2",     CAMERA),
    ("VTX — PFLY 2.5W / 3W  5.8GHz  [Phase 1 only — removed at Phase 2]", "×2",     VTX),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]",    "×2",     ANTENNA_ICON),
    ("Receiver — BETAFPV SuperD Diversity 2.4G ELRS",                      "×2",     RX_DIV_ICON),
    ("Propeller — HQProp 10X5X3  3-blade  4pcs/set",                       "×4 set", PROP_HQ),
    ("Propeller spare — Gemfan 9045  3-blade  2pcs/set",                   "×4 set", PROP_GF),
    ("Battery — 6S 22.2V 6500mAh LiPo  XT60",                             "×2",     BAT_6S),
]
elements += make_section("◼  10-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", ten_phase1)

# ── Section 2: 15-inch Quad ───────────────────────────────────────────────
fifteen_phase1 = [
    ("Frame — 15-inch Long Range Carbon Fiber Frame",                      "×2",      FRAME15_ICON),
    ("Motor — MAD BSC 4214 380KV  (4 per drone)",                          "×8",      MOTOR_15),
    ("Motor spare — MAD BSC 4214 380KV",                                   "×2",      MOTOR_15),
    ("FC / ESC Stack — F7 FC + 4-in-1 100A ESC  3–8S",                    "×2",      FCESC),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens",                        "×2",      CAMERA),
    ("VTX — PFLY 3W  5.8GHz  [Phase 1 only — removed at Phase 2]",        "×2",      VTX),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]",    "×2",      ANTENNA_ICON),
    ("Receiver — ELRS 2.4G Nano Receiver",                                 "×2",      RX_ELRS),
    ("Propeller — 15-inch 3-blade Props  (2 pairs per drone)",             "×4 pair", PROP15_ICON),
    ("Battery — 8S 29.6V 16000mAh LiPo  XT90  (2 per drone)",             "×4",      BAT_8S),
    ("Battery Protective Hard Case",                                        "×2",      CASE_ICON),
]
elements += make_section("◼  15-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", fifteen_phase1)

# ── Section 3: Shared items ───────────────────────────────────────────────
shared = [
    ("Remote Controller — Radiomaster TX16S MarkII ELRS  (1 per pilot)", "×2",     TX16S_ICON),
    ("Charger — ISDT Q8 Max  8S Compatible",                             "×2",     CHARGER_ICON),
    ("DC Power Supply — 30V 30A",                                        "×2",     PSU_ICON),
    ("Connector — XT90 Male+Female Pair",                                "×20 pr", CONN_ICON),
    ("Connector — XT60 Male+Female Pair",                                "×10 pr", CONN_ICON),
    ("Silicone Wire — 12AWG Red + Black  5m each",                       "×2 roll",WIRE_ICON),
    ("Capacitor — 50V 1000µF Low ESR  (ESC filter)",                     "×10",    CAP_ICON),
    ("Heat Shrink Tubing — Assorted set",                                "×2 set", HEAT_ICON),
    ("Velcro Battery Strap — 250–300mm",                                 "×12",    VELCRO_ICON),
    ("Solder Wire — 63/37 Rosin Core 1mm",                               "×2 roll",SOLDER_ICON),
    ("Hex Driver Set — 1.5 / 2 / 2.5 / 3mm",                            "×1 set", HEX_ICON),
]
elements += make_section("◼  SHARED ITEMS  |  Common to All Drones", shared)

# ── Section 4: Phase 2 Fiber ──────────────────────────────────────────────
fiber = [
    ("Optical Fiber Module — 15KM  Sky Module + Ground Station", "×1 set", FIBER_ICON),
    ("Fiber Patch Cable spare — Single Mode LC/APC  5m",         "×2",     FCBL_ICON),
    ("Fiber Cable Protective Sleeve",                             "×1",     FSLV_ICON),
]
elements += make_section(
    "◼  PHASE 2 — FIBER OPTIC UPGRADE  |  All 4 Drones",
    fiber,
    hdr_color=LIGHT, qty_color=LIGHT,
)

# ── Phase note ─────────────────────────────────────────────────────────────
note = Table([[Paragraph(
    "<b>Phase 2 Note:</b>  Remove VTX + VTX Antenna from each drone.  "
    "Connect Optical Fiber Sky Module.  Camera, FC, Motors, Battery — no changes.",
    ParagraphStyle("note", fontName="Helvetica", fontSize=8,
                   textColor=DARK, alignment=TA_LEFT, leading=12),
)]], colWidths=[sum(cw)])
note.setStyle(TableStyle([
    ("BACKGROUND",  (0,0), (-1,-1), colors.HexColor("#fff8e1")),
    ("ROWPADDING",  (0,0), (-1,-1), 8),
    ("LEFTPADDING", (0,0), (-1,-1), 10),
    ("BOX",         (0,0), (-1,-1), 1, colors.HexColor("#f0c040")),
]))
elements.append(note)

# ── Footer ─────────────────────────────────────────────────────────────────
elements.append(Spacer(1, 0.4*cm))
foot = Table([[
    Paragraph("Project: 10-inch + 15-inch Quad Build",
              ParagraphStyle("f1", fontName="Helvetica", fontSize=7.5,
                             textColor=colors.gray, alignment=TA_LEFT)),
    Paragraph("USD/CNY: 6.80  |  May 2026",
              ParagraphStyle("f2", fontName="Helvetica", fontSize=7.5,
                             textColor=colors.gray, alignment=TA_RIGHT)),
]], colWidths=[9*cm, 8.5*cm])
elements.append(foot)

doc.build(elements)
print("PDF created: parts-list.pdf")
