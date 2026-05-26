from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.platypus import Image as RLImage
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from PIL import Image as PILImage
from io import BytesIO
import os

UPLOADS = "/root/.claude/uploads/fe593dea-afe8-45a3-a7c0-ffe46d5eddbc/"
IMG_W = 1.8 * cm
IMG_H = 1.8 * cm


def make_img_cell(path, crop_px=None):
    """Open image, optionally crop to crop_px=(left,top,right,bottom),
    resize to square thumbnail, return RLImage or '' on error."""
    try:
        im = PILImage.open(path).convert("RGB")
        if crop_px:
            im = im.crop(crop_px)
        im.thumbnail((120, 120), PILImage.LANCZOS)
        canvas = PILImage.new("RGB", (120, 120), (255, 255, 255))
        ox = (120 - im.size[0]) // 2
        oy = (120 - im.size[1]) // 2
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
    return make_img_cell(spec[0], spec[1])


doc = SimpleDocTemplate(
    "/home/user/jdjv/parts-list.pdf",
    pagesize=A4,
    rightMargin=1.5*cm,
    leftMargin=1.5*cm,
    topMargin=1.5*cm,
    bottomMargin=1.5*cm,
)

styles = getSampleStyleSheet()
W, H = A4

DARK   = colors.HexColor("#1a1a2e")
BLUE   = colors.HexColor("#16213e")
ACCENT = colors.HexColor("#0f3460")
LIGHT  = colors.HexColor("#e94560")
GRAY   = colors.HexColor("#f0f0f0")
WHITE  = colors.white

# ── Image specs: (path, crop_box_pixels or None) ───────────────────────────
CAMERA   = (UPLOADS + "7ca46921-1000600883.jpg",  None)
MOTOR_15 = (UPLOADS + "66c7838c-1000601847.jpg",  (580, 0, 1550, 904))
VTX      = (UPLOADS + "a6004eb7-1000600890.jpg",  (30, 60, 480, 420))
RX_ELRS  = (UPLOADS + "e49fa772-1000600893.jpg",  (0, 50, 500, 520))
BAT_8S   = (UPLOADS + "b1fce124-1000600668.jpg",  (50, 100, 720, 900))
BAT_6S   = (UPLOADS + "cd573ff2-1000600709.jpg",  (50, 200, 650, 1000))
FRAME_10 = (UPLOADS + "9cb1854b-1000601602.jpg",  (400, 0, 3600, 3000))
FCESC    = (UPLOADS + "73ee8b32-1000601609.jpg",  (1300, 0, 2800, 1560))
PROP_HQ  = (UPLOADS + "2dff912b-1000600888.jpg",  (250, 50, 500, 530))
PROP_GF  = (UPLOADS + "8f510c44-1000600887.jpg",  (0, 50, 250, 450))


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

# ── TITLE ──────────────────────────────────────────────────────────────────
title_table = Table(
    [[Paragraph("QUAD BUILD — PARTS LIST", header_style(16)),
      Paragraph("10-inch  &amp;  15-inch", header_style(10))]],
    colWidths=[12*cm, 5.5*cm]
)
title_table.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), DARK),
    ("ROWPADDING", (0,0), (-1,-1), 10),
    ("ALIGN", (0,0), (-1,-1), "CENTER"),
    ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
    ("ROUNDEDCORNERS", [6,6,6,6]),
]))
elements.append(title_table)
elements.append(Spacer(1, 0.4*cm))

st = Table([[
    Paragraph("Phase 1: Full Radio + FPV Build", sub_style(9)),
    Paragraph("Phase 2: Fiber Optic Upgrade (swap VTX only)", sub_style(9)),
]], colWidths=[8.5*cm, 9*cm])
st.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), GRAY),
    ("ROWPADDING", (0,0), (-1,-1), 6),
    ("GRID", (0,0), (-1,-1), 0.5, colors.HexColor("#cccccc")),
]))
elements.append(st)
elements.append(Spacer(1, 0.5*cm))

# Column widths: [photo, #, name, qty]  total = 17.5cm
cw = [2.0*cm, 0.8*cm, 11.2*cm, 3.5*cm]

_CH = {"fontName": "Helvetica-Bold", "fontSize": 9, "textColor": DARK}


def make_section(title_text, rows, col_widths=None):
    """rows: list of (name, qty, img_spec) triples."""
    if col_widths is None:
        col_widths = cw
    section = []

    hdr = Table([[Paragraph(title_text, section_style())]],
                colWidths=[sum(col_widths)])
    hdr.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), ACCENT),
        ("ROWPADDING", (0,0), (-1,-1), 7),
        ("LEFTPADDING", (0,0), (-1,-1), 10),
    ]))
    section.append(hdr)

    col_header = Table([[
        Paragraph("", ParagraphStyle("ph", **_CH, alignment=TA_CENTER)),
        Paragraph("#",  ParagraphStyle("ch", **_CH, alignment=TA_CENTER)),
        Paragraph("Part / Component", ParagraphStyle("ch2", **_CH, alignment=TA_LEFT)),
        Paragraph("Qty", ParagraphStyle("ch3", **_CH, alignment=TA_CENTER)),
    ]], colWidths=col_widths)
    col_header.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), colors.HexColor("#dce8f5")),
        ("ROWPADDING", (0,0), (-1,-1), 5),
        ("GRID", (0,0), (-1,-1), 0.3, colors.HexColor("#aaaaaa")),
    ]))
    section.append(col_header)

    for i, (name, qty, img_spec) in enumerate(rows):
        bg = WHITE if i % 2 == 0 else GRAY
        row = Table([[
            img(img_spec),
            Paragraph(str(i+1), ParagraphStyle("n", fontName="Helvetica", fontSize=8,
                                               textColor=colors.HexColor("#666666"), alignment=TA_CENTER)),
            Paragraph(name, ParagraphStyle("nm", fontName="Helvetica", fontSize=8.5,
                                           textColor=DARK, alignment=TA_LEFT)),
            Paragraph(qty,  ParagraphStyle("q", fontName="Helvetica-Bold", fontSize=8.5,
                                           textColor=ACCENT, alignment=TA_CENTER)),
        ]], colWidths=col_widths)
        row.setStyle(TableStyle([
            ("BACKGROUND", (0,0), (-1,-1), bg),
            ("ROWPADDING", (0,0), (-1,-1), 4),
            ("GRID", (0,0), (-1,-1), 0.2, colors.HexColor("#cccccc")),
            ("LEFTPADDING", (2,0), (2,0), 8),
            ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
            ("ALIGN", (0,0), (0,-1), "CENTER"),
        ]))
        section.append(row)

    section.append(Spacer(1, 0.4*cm))
    return section


# ── SECTION 1: 10-inch Quad ────────────────────────────────────────────────
ten_phase1 = [
    ("Frame — Mark4 V2 10-inch Carbon Fiber",                             "×2",     FRAME_10),
    ("Motor — Arcosky 3115 900KV  (4 per drone)",                         "×8",     None),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (invoice)",         "×5",     None),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (additional)",      "×4",     None),
    ("FC / ESC Stack — F722 F405 55A All-in-One",                         "×2",     FCESC),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens",                       "×2",     CAMERA),
    ("VTX — PFLY 2.5W / 3W  5.8GHz  [Phase 1 only — removed at Phase 2]","×2",     VTX),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]",   "×2",     None),
    ("Receiver — BETAFPV SuperD Diversity 2.4G ELRS",                     "×2",     None),
    ("Propeller — HQProp 10X5X3  3-blade  4pcs/set",                      "×4 set", PROP_HQ),
    ("Propeller spare — Gemfan 9045  3-blade  2pcs/set",                  "×4 set", PROP_GF),
    ("Battery — 6S 22.2V 6500mAh LiPo  XT60",                            "×2",     BAT_6S),
]
elements += make_section("◼  10-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", ten_phase1)

# ── SECTION 2: 15-inch Quad ───────────────────────────────────────────────
fifteen_phase1 = [
    ("Frame — 15-inch Long Range Carbon Fiber Frame",                     "×2",     None),
    ("Motor — MAD BSC 4214 380KV  (4 per drone)",                         "×8",     MOTOR_15),
    ("Motor spare — MAD BSC 4214 380KV",                                  "×2",     MOTOR_15),
    ("FC / ESC Stack — F7 FC + 4-in-1 100A ESC  3–8S",                   "×2",     FCESC),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens",                       "×2",     CAMERA),
    ("VTX — PFLY 3W  5.8GHz  [Phase 1 only — removed at Phase 2]",       "×2",     VTX),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]",   "×2",     None),
    ("Receiver — ELRS 2.4G Nano Receiver",                                "×2",     RX_ELRS),
    ("Propeller — 15-inch 3-blade Props  (2 pairs per drone)",            "×4 pair",None),
    ("Battery — 8S 29.6V 16000mAh LiPo  XT90  (2 per drone)",            "×4",     BAT_8S),
    ("Battery Protective Hard Case",                                       "×2",     None),
]
elements += make_section("◼  15-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", fifteen_phase1)

# ── SECTION 3: Shared ─────────────────────────────────────────────────────
shared = [
    ("Remote Controller — Radiomaster TX16S MarkII ELRS  (1 per pilot)", "×2",     None),
    ("Charger — ISDT Q8 Max  8S Compatible",                             "×2",     None),
    ("DC Power Supply — 30V 30A",                                        "×2",     None),
    ("Connector — XT90 Male+Female Pair",                                "×20 pr", None),
    ("Connector — XT60 Male+Female Pair",                                "×10 pr", None),
    ("Silicone Wire — 12AWG Red + Black  5m each",                       "×2 roll",None),
    ("Capacitor — 50V 1000µF Low ESR  (ESC filter)",                     "×10",    None),
    ("Heat Shrink Tubing — Assorted set",                                "×2 set", None),
    ("Velcro Battery Strap — 250–300mm",                                 "×12",    None),
    ("Solder Wire — 63/37 Rosin Core 1mm",                               "×2 roll",None),
    ("Hex Driver Set — 1.5 / 2 / 2.5 / 3mm",                            "×1 set", None),
]
elements += make_section("◼  SHARED ITEMS  |  Common to All Drones", shared)

# ── SECTION 4: Phase 2 Fiber ──────────────────────────────────────────────
fiber = [
    ("Optical Fiber Module — 15KM  Sky Module + Ground Station", "×1 set", None),
    ("Fiber Patch Cable spare — Single Mode LC/APC  5m",         "×2",     None),
    ("Fiber Cable Protective Sleeve",                             "×1",     None),
]

p2_hdr = Table([[Paragraph("◼  PHASE 2 — FIBER OPTIC UPGRADE  |  All 4 Drones", section_style())]],
               colWidths=[sum(cw)])
p2_hdr.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), LIGHT),
    ("ROWPADDING", (0,0), (-1,-1), 7),
    ("LEFTPADDING", (0,0), (-1,-1), 10),
]))
elements.append(p2_hdr)

col_header2 = Table([[
    Paragraph("", ParagraphStyle("ph", **_CH, alignment=TA_CENTER)),
    Paragraph("#",  ParagraphStyle("ch", **_CH, alignment=TA_CENTER)),
    Paragraph("Part / Component", ParagraphStyle("ch2", **_CH, alignment=TA_LEFT)),
    Paragraph("Qty", ParagraphStyle("ch3", **_CH, alignment=TA_CENTER)),
]], colWidths=cw)
col_header2.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), colors.HexColor("#fde8ec")),
    ("ROWPADDING", (0,0), (-1,-1), 5),
    ("GRID", (0,0), (-1,-1), 0.3, colors.HexColor("#aaaaaa")),
]))
elements.append(col_header2)

for i, (name, qty, img_spec) in enumerate(fiber):
    bg = WHITE if i % 2 == 0 else GRAY
    row = Table([[
        img(img_spec),
        Paragraph(str(i+1), ParagraphStyle("n", fontName="Helvetica", fontSize=8,
                                           textColor=colors.HexColor("#666666"), alignment=TA_CENTER)),
        Paragraph(name, ParagraphStyle("nm", fontName="Helvetica", fontSize=8.5,
                                       textColor=DARK, alignment=TA_LEFT)),
        Paragraph(qty,  ParagraphStyle("q", fontName="Helvetica-Bold", fontSize=8.5,
                                       textColor=LIGHT, alignment=TA_CENTER)),
    ]], colWidths=cw)
    row.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), bg),
        ("ROWPADDING", (0,0), (-1,-1), 4),
        ("GRID", (0,0), (-1,-1), 0.2, colors.HexColor("#cccccc")),
        ("LEFTPADDING", (2,0), (2,0), 8),
        ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
        ("ALIGN", (0,0), (0,-1), "CENTER"),
    ]))
    elements.append(row)

elements.append(Spacer(1, 0.5*cm))

# ── PHASE NOTE ────────────────────────────────────────────────────────────
note = Table([[
    Paragraph(
        "<b>Phase 2 Note:</b>  Remove VTX + VTX Antenna from each drone.  "
        "Connect Optical Fiber Sky Module.  Camera, FC, Motors, Battery — no changes.",
        ParagraphStyle("note", fontName="Helvetica", fontSize=8, textColor=DARK,
                       alignment=TA_LEFT, leading=12)
    )
]], colWidths=[sum(cw)])
note.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), colors.HexColor("#fff8e1")),
    ("ROWPADDING", (0,0), (-1,-1), 8),
    ("LEFTPADDING", (0,0), (-1,-1), 10),
    ("BOX", (0,0), (-1,-1), 1, colors.HexColor("#f0c040")),
]))
elements.append(note)

# ── FOOTER ────────────────────────────────────────────────────────────────
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
