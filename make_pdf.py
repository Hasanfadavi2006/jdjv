from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, HRFlowable
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
import os

# Try to register a font that supports Persian/Arabic (fallback to built-in if not available)
font_name = "Helvetica"

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

# Color palette
DARK   = colors.HexColor("#1a1a2e")
BLUE   = colors.HexColor("#16213e")
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

def title_style():
    return ParagraphStyle("title", fontName="Helvetica-Bold", fontSize=16,
                          textColor=DARK, alignment=TA_CENTER, leading=22)

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

subtitle_data = [[
    Paragraph("Phase 1: Full Radio + FPV Build", sub_style(9)),
    Paragraph("Phase 2: Fiber Optic Upgrade (swap VTX only)", sub_style(9)),
]]
st = Table(subtitle_data, colWidths=[8.5*cm, 9*cm])
st.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), GRAY),
    ("ROWPADDING", (0,0), (-1,-1), 6),
    ("GRID", (0,0), (-1,-1), 0.5, colors.HexColor("#cccccc")),
]))
elements.append(st)
elements.append(Spacer(1, 0.5*cm))

# ── Helper to build a section ──────────────────────────────────────────────
def make_section(title_text, rows, col_widths):
    """rows: list of (item_name, qty_str) tuples"""
    section = []

    # Section header
    hdr = Table([[Paragraph(title_text, section_style())]],
                colWidths=[sum(col_widths)])
    hdr.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), ACCENT),
        ("ROWPADDING", (0,0), (-1,-1), 7),
        ("LEFTPADDING", (0,0), (-1,-1), 10),
    ]))
    section.append(hdr)

    # Column headers
    col_header = Table(
        [[Paragraph("#", ParagraphStyle("ch", fontName="Helvetica-Bold", fontSize=9,
                                        textColor=DARK, alignment=TA_CENTER)),
          Paragraph("Part / Component", ParagraphStyle("ch2", fontName="Helvetica-Bold", fontSize=9,
                                                        textColor=DARK, alignment=TA_LEFT)),
          Paragraph("Qty", ParagraphStyle("ch3", fontName="Helvetica-Bold", fontSize=9,
                                           textColor=DARK, alignment=TA_CENTER))]],
        colWidths=col_widths
    )
    col_header.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), colors.HexColor("#dce8f5")),
        ("ROWPADDING", (0,0), (-1,-1), 5),
        ("GRID", (0,0), (-1,-1), 0.3, colors.HexColor("#aaaaaa")),
    ]))
    section.append(col_header)

    # Data rows
    for i, (name, qty) in enumerate(rows):
        bg = WHITE if i % 2 == 0 else GRAY
        row = Table(
            [[Paragraph(str(i+1), ParagraphStyle("n", fontName="Helvetica", fontSize=8,
                                                   textColor=colors.HexColor("#666666"), alignment=TA_CENTER)),
              Paragraph(name, ParagraphStyle("nm", fontName="Helvetica", fontSize=8.5,
                                              textColor=DARK, alignment=TA_LEFT)),
              Paragraph(qty, ParagraphStyle("q", fontName="Helvetica-Bold", fontSize=8.5,
                                             textColor=ACCENT, alignment=TA_CENTER))]],
            colWidths=col_widths
        )
        row.setStyle(TableStyle([
            ("BACKGROUND", (0,0), (-1,-1), bg),
            ("ROWPADDING", (0,0), (-1,-1), 5),
            ("GRID", (0,0), (-1,-1), 0.2, colors.HexColor("#cccccc")),
            ("LEFTPADDING", (1,0), (1,0), 8),
        ]))
        section.append(row)

    section.append(Spacer(1, 0.4*cm))
    return section

cw = [1*cm, 13.5*cm, 3*cm]  # #, name, qty

# ── SECTION 1: 10-inch Quad ────────────────────────────────────────────────
ten_phase1 = [
    ("Frame — Mark4 V2 10-inch Carbon Fiber", "×2"),
    ("Motor — Arcosky 3115 900KV  (4 per drone)", "×8"),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (invoice)", "×5"),
    ("Motor — BrotherHobby Tornado T5 3115 PRO 900KV  (additional)", "×4"),
    ("FC / ESC Stack — F722 F405 55A All-in-One", "×2"),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens", "×2"),
    ("VTX — PFLY 2.5W / 3W  5.8GHz  [Phase 1 only — removed at Phase 2]", "×2"),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]", "×2"),
    ("Receiver — BETAFPV SuperD Diversity 2.4G ELRS", "×2"),
    ("Propeller — HQProp 10X5X3  3-blade  4pcs/set", "×4 set"),
    ("Propeller spare — Gemfan 9045  3-blade  2pcs/set", "×4 set"),
    ("Battery — 6S 22.2V 6500mAh LiPo  XT60", "×2"),
]

elements += make_section("◼  10-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", ten_phase1, cw)

# ── SECTION 2: 15-inch Quad ───────────────────────────────────────────────
fifteen_phase1 = [
    ("Frame — 15-inch Long Range Carbon Fiber Frame", "×2"),
    ("Motor — MAD BSC 4214 380KV  (4 per drone)", "×8"),
    ("Motor spare — MAD BSC 4214 380KV", "×2"),
    ("FC / ESC Stack — F7 FC + 4-in-1 100A ESC  3–8S", "×2"),
    ("Camera — RunCam Phoenix 2 SP V5  2.1mm Lens", "×2"),
    ("VTX — PFLY 3W  5.8GHz  [Phase 1 only — removed at Phase 2]", "×2"),
    ("VTX Antenna — Lumenier AXII 2 Long Range 5.8GHz  [Phase 1 only]", "×2"),
    ("Receiver — ELRS 2.4G Nano Receiver", "×2"),
    ("Propeller — 15-inch 3-blade Props  (2 pairs per drone)", "×4 pair"),
    ("Battery — 8S 29.6V 16000mAh LiPo  XT90  (2 per drone)", "×4"),
    ("Battery Protective Hard Case", "×2"),
]

elements += make_section("◼  15-INCH QUAD  ×2  |  Phase 1 — Full FPV Build", fifteen_phase1, cw)

# ── SECTION 3: Shared ─────────────────────────────────────────────────────
shared = [
    ("Remote Controller — Radiomaster TX16S MarkII ELRS  (1 per pilot)", "×2"),
    ("Charger — ISDT Q8 Max  8S Compatible", "×2"),
    ("DC Power Supply — 30V 30A", "×2"),
    ("Connector — XT90 Male+Female Pair", "×20 pr"),
    ("Connector — XT60 Male+Female Pair", "×10 pr"),
    ("Silicone Wire — 12AWG Red + Black  5m each", "×2 roll"),
    ("Capacitor — 50V 1000µF Low ESR  (ESC filter)", "×10"),
    ("Heat Shrink Tubing — Assorted set", "×2 set"),
    ("Velcro Battery Strap — 250–300mm", "×12"),
    ("Solder Wire — 63/37 Rosin Core 1mm", "×2 roll"),
    ("Hex Driver Set — 1.5 / 2 / 2.5 / 3mm", "×1 set"),
]

elements += make_section("◼  SHARED ITEMS  |  Common to All Drones", shared, cw)

# ── SECTION 4: Phase 2 Fiber ──────────────────────────────────────────────
fiber = [
    ("Optical Fiber Module — 15KM  Sky Module + Ground Station", "×1 set"),
    ("Fiber Patch Cable spare — Single Mode LC/APC  5m", "×2"),
    ("Fiber Cable Protective Sleeve", "×1"),
]

# Special header for phase 2
p2_hdr = Table([[Paragraph("◼  PHASE 2 — FIBER OPTIC UPGRADE  |  All 4 Drones",
                             section_style())]],
               colWidths=[sum(cw)])
p2_hdr.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), LIGHT),
    ("ROWPADDING", (0,0), (-1,-1), 7),
    ("LEFTPADDING", (0,0), (-1,-1), 10),
]))
elements.append(p2_hdr)

col_header2 = Table(
    [[Paragraph("#", ParagraphStyle("ch", fontName="Helvetica-Bold", fontSize=9,
                                     textColor=DARK, alignment=TA_CENTER)),
      Paragraph("Part / Component", ParagraphStyle("ch2", fontName="Helvetica-Bold", fontSize=9,
                                                    textColor=DARK, alignment=TA_LEFT)),
      Paragraph("Qty", ParagraphStyle("ch3", fontName="Helvetica-Bold", fontSize=9,
                                       textColor=DARK, alignment=TA_CENTER))]],
    colWidths=cw
)
col_header2.setStyle(TableStyle([
    ("BACKGROUND", (0,0), (-1,-1), colors.HexColor("#fde8ec")),
    ("ROWPADDING", (0,0), (-1,-1), 5),
    ("GRID", (0,0), (-1,-1), 0.3, colors.HexColor("#aaaaaa")),
]))
elements.append(col_header2)

for i, (name, qty) in enumerate(fiber):
    bg = WHITE if i % 2 == 0 else GRAY
    row = Table(
        [[Paragraph(str(i+1), ParagraphStyle("n", fontName="Helvetica", fontSize=8,
                                               textColor=colors.HexColor("#666666"), alignment=TA_CENTER)),
          Paragraph(name, ParagraphStyle("nm", fontName="Helvetica", fontSize=8.5,
                                          textColor=DARK, alignment=TA_LEFT)),
          Paragraph(qty, ParagraphStyle("q", fontName="Helvetica-Bold", fontSize=8.5,
                                         textColor=LIGHT, alignment=TA_CENTER))]],
        colWidths=cw
    )
    row.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,-1), bg),
        ("ROWPADDING", (0,0), (-1,-1), 5),
        ("GRID", (0,0), (-1,-1), 0.2, colors.HexColor("#cccccc")),
        ("LEFTPADDING", (1,0), (1,0), 8),
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
