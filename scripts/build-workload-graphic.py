#!/usr/bin/env python3
"""
Builds docs/workload-distribution.jpg (16:9 slide graphic) showing the CRUD workload per member.
Counts come from the CRUD breakdown in docs/CRUD_BY_MEMBER.md (see OPS below).

Requires: pip install weasyprint pymupdf
Run from the project root:  python3 scripts/build-workload-graphic.py
"""
import math
from pathlib import Path

import pymupdf
from weasyprint import HTML

ROOT = Path(__file__).resolve().parent.parent
OUT_JPG = ROOT / "docs" / "workload-distribution.jpg"

MEMBERS = [
    # number, student id, component, accent, Create, Read, Update, Delete, File(write)
    (1, "IT26100914", "User & Authentication", "#7c3aed", 2, 4, 2, 3, 0),
    (2, "IT26100915", "Movie Catalogue", "#db2777", 1, 2, 1, 1, 0),
    (3, "IT26100916", "Theatre & Screen", "#0891b2", 2, 2, 2, 2, 0),
    (4, "IT26100918", "Showtime Scheduling", "#4f46e5", 1, 2, 1, 1, 0),
    (5, "IT26100919", "Booking & Seat Reservation", "#0d9488", 1, 2, 1, 2, 0),
    (6, "IT26100920", "Payment & File Handling", "#ea580c", 1, 4, 1, 1, 1),
]

OP_COLORS = {
    "Create": "#16a34a",
    "Read": "#2563eb",
    "Update": "#d97706",
    "Delete": "#dc2626",
    "File": "#475569",
}
OP_KEYS = ["Create", "Read", "Update", "Delete", "File"]


def totals():
    rows = []
    for m in MEMBERS:
        ops = m[4:]
        rows.append((m, sum(ops)))
    return rows


def donut_paths(values, cx, cy, r_out, r_in):
    """Returns SVG paths for a donut split into wedges proportional to values."""
    total = sum(v for _, v in values)
    paths = []
    angle = -math.pi / 2  # start at 12 o'clock
    for color, v in values:
        if v == 0:
            continue
        sweep = 2 * math.pi * v / total
        end = angle + sweep
        large = 1 if sweep > math.pi else 0
        x1, y1 = cx + r_out * math.cos(angle), cy + r_out * math.sin(angle)
        x2, y2 = cx + r_out * math.cos(end), cy + r_out * math.sin(end)
        x3, y3 = cx + r_in * math.cos(end), cy + r_in * math.sin(end)
        x4, y4 = cx + r_in * math.cos(angle), cy + r_in * math.sin(angle)
        d = (f"M {x1:.2f} {y1:.2f} A {r_out} {r_out} 0 {large} 1 {x2:.2f} {y2:.2f} "
             f"L {x3:.2f} {y3:.2f} A {r_in} {r_in} 0 {large} 0 {x4:.2f} {y4:.2f} Z")
        paths.append(f'<path d="{d}" fill="{color}" stroke="#ffffff" stroke-width="2"/>')
        angle = end
    return "".join(paths)


def bar_rows_html(rows, grand_total):
    unit = 34  # px per operation in the bar (SVG units)
    out = []
    for (m, total) in rows:
        num, mid, name, accent, *ops = m
        segs = []
        x = 0
        for key, val in zip(OP_KEYS, ops[:5]):
            if val == 0:
                continue
            w = val * unit
            segs.append(
                f'<rect x="{x}" y="0" width="{w}" height="34" fill="{OP_COLORS[key]}"/>'
                f'<text x="{x + w / 2}" y="22" text-anchor="middle" font-size="15" font-weight="bold" fill="#ffffff">{val}</text>'
            )
            x += w
        pct = f'{100 * total / grand_total:.1f}'
        out.append(f"""
        <div class="barrow">
          <div class="barlabel">
            <div class="mnum" style="color:{accent}">MEMBER {num}</div>
            <div class="mname">{name}</div>
            <div class="mid">{mid}</div>
          </div>
          <div class="bar"><svg width="{unit * 11}" height="34" viewBox="0 0 {unit * 11} 34">{''.join(segs)}</svg></div>
          <div class="barval"><span class="tot">{total}</span><span class="pct">{pct}%</span></div>
        </div>""")
    return "".join(out)


def card_html(m, total, grand_total):
    num, mid, name, accent, *_ = m
    pct = f'{100 * total / grand_total:.1f}'
    return f"""
    <div class="card" style="border-top:6px solid {accent}">
      <div class="cnum" style="color:{accent}">MEMBER {num}</div>
      <div class="cid">{mid}</div>
      <div class="cname">{name}</div>
      <div class="cstat"><b>{total}</b> ops · <b>{pct}%</b> of workload</div>
    </div>"""


def build_html():
    rows = totals()
    grand = sum(t for _, t in rows)
    donut_vals = [(MEMBERS[i][3], rows[i][1]) for i in range(len(MEMBERS))]
    donut = donut_paths(donut_vals, 150, 150, 140, 84)
    cards = "".join(card_html(m, t, grand) for m, t in rows)
    legend = "".join(
        f'<span class="leg"><i style="background:{OP_COLORS[k]}"></i>{k if k != "File" else "File (write)"}</span>'
        for k in OP_KEYS)
    member_legend = "".join(
        f'<span class="leg"><i style="background:{m[3]}"></i>M{m[0]} {m[2]}</span>' for m in MEMBERS)
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@page {{ size: 13.333in 7.5in; margin: 0; }}
body {{ margin: 0; font-family: 'DejaVu Sans', sans-serif; color: #111827; width: 13.333in; height: 7.5in; background: #f8fafc; }}
.wrap {{ position: relative; width: 13.333in; height: 7.5in; overflow: hidden; }}
.head {{ height: 1.3in; background: linear-gradient(120deg, #1e1b4b 0%, #4c1d95 50%, #be185d 100%); color: #fff; padding: 0.32in 0.55in 0 0.55in; box-sizing: border-box; }}
.kicker {{ font-size: 10pt; letter-spacing: 2pt; text-transform: uppercase; color: #f9a8d4; }}
.title {{ font-size: 28pt; font-weight: bold; margin-top: 4pt; }}
.sub {{ font-size: 11pt; color: #e9d5ff; margin-top: 2pt; }}
.cards {{ position: absolute; top: 1.5in; left: 0.55in; right: 0.55in; }}
table.ct {{ width: 100%; border-collapse: separate; border-spacing: 10px 0; margin: 0 -10px; }}
table.ct td {{ width: 33%; vertical-align: top; }}
.card {{ background: #ffffff; border-radius: 8pt; padding: 6pt 10pt 6pt 10pt; box-shadow: 0 1px 2px rgba(0,0,0,0.08); }}
.cnum {{ font-size: 8pt; letter-spacing: 1.2pt; font-weight: bold; }}
.cid {{ font-size: 13pt; font-weight: bold; margin: 2pt 0 2pt 0; color: #111827; }}
.cname {{ font-size: 9pt; color: #374151; }}
.cstat {{ font-size: 8.5pt; color: #6b7280; margin-top: 3pt; border-top: 1px solid #e5e7eb; padding-top: 3pt; }}
.panel {{ position: absolute; top: 3.85in; left: 0.55in; width: 8.6in; height: 3.2in; background: #ffffff; border-radius: 10pt; padding: 12pt 16pt; box-sizing: border-box; box-shadow: 0 1px 2px rgba(0,0,0,0.08); }}
.ptitle {{ font-size: 12pt; font-weight: bold; color: #111827; }}
.psub {{ font-size: 8.5pt; color: #6b7280; margin-bottom: 6pt; }}
.barrow {{ height: 0.36in; }}
.barrow > div {{ display: inline-block; vertical-align: middle; }}
.barlabel {{ width: 3.0in; }}
.mnum {{ font-size: 7pt; letter-spacing: 1pt; font-weight: bold; }}
.mname {{ font-size: 9pt; color: #111827; display: inline-block; }}
.mid {{ font-size: 8pt; color: #6b7280; display: inline-block; margin-left: 6pt; }}
.bar {{ width: 3.9in; }}
.barval {{ width: 0.9in; text-align: right; }}
.tot {{ font-size: 12pt; font-weight: bold; color: #111827; }}
.pct {{ font-size: 9pt; color: #6b7280; margin-left: 6pt; }}
.legend {{ margin-top: 6pt; }}
.leg {{ display: inline-block; font-size: 8.5pt; color: #374151; margin-right: 12pt; }}
.leg i {{ display: inline-block; width: 9pt; height: 9pt; border-radius: 2pt; margin-right: 4pt; vertical-align: middle; }}
.donutbox {{ position: absolute; top: 3.85in; right: 0.55in; width: 4.1in; height: 3.2in; background: #ffffff; border-radius: 10pt; padding: 12pt 14pt; box-sizing: border-box; box-shadow: 0 1px 2px rgba(0,0,0,0.08); }}
.foot {{ position: absolute; bottom: 0.14in; left: 0.55in; right: 0.55in; font-size: 8.5pt; color: #6b7280; }}
</style></head><body><div class="wrap">
  <div class="head">
    <div class="kicker">SE1020 · Object Oriented Programming Project</div>
    <div class="title">Team Workload Distribution</div>
    <div class="sub">Movie Ticket Booking System · 6 members · {grand} CRUD operations in total</div>
  </div>

  <div class="cards">
    <table class="ct"><tr>{''.join(f'<td>{card_html(m, t, grand)}</td>' for m, t in rows[:3])}</tr>
    <tr><td style="height:8px"></td><td></td><td></td></tr>
    <tr>{''.join(f'<td>{card_html(m, t, grand)}</td>' for m, t in rows[3:])}</tr></table>
  </div>

  <div class="panel">
    <div class="ptitle">Operations per member, by type</div>
    <div class="psub">Each bar is one member. Segments show Create, Read, Update, Delete and File (write) operations.</div>
    {bar_rows_html(rows, grand)}
    <div class="legend">{legend}</div>
  </div>

  <div class="donutbox">
    <div class="ptitle">Share of total workload</div>
    <div class="psub">Percentage of the {grand} operations per member</div>
    <table style="width:100%"><tr><td style="width:2.2in">
      <svg width="2.1in" height="2.1in" viewBox="0 0 300 300">
        {donut_paths(donut_vals, 150, 150, 140, 84)}
        <text x="150" y="150" text-anchor="middle" font-size="44" font-weight="bold" fill="#111827">{grand}</text>
        <text x="150" y="176" text-anchor="middle" font-size="18" fill="#6b7280">operations</text>
      </svg>
    </td><td style="vertical-align:middle">
      {''.join(f'<div style="font-size:8.5pt;margin:3pt 0"><i style="display:inline-block;width:9pt;height:9pt;background:{m[3]};border-radius:2pt;margin-right:4pt"></i>M{m[0]} · {f'{100*t/grand:.1f}'}%</div>' for m, t in rows)}
    </td></tr></table>
  </div>

  <div class="foot">Source: docs/CRUD_BY_MEMBER.md · Member IDs: IT26100914 (1), IT26100915 (2), IT26100916 (3), IT26100918 (4), IT26100919 (5), IT26100920 (6)</div>
</div></body></html>"""


def main():
    html = build_html()
    pdf_bytes = HTML(string=html, base_url=str(ROOT / "docs")).write_pdf()
    doc = pymupdf.open(stream=pdf_bytes, filetype="pdf")
    pix = doc[0].get_pixmap(dpi=200)
    OUT_JPG.parent.mkdir(parents=True, exist_ok=True)
    pix.save(str(OUT_JPG), jpg_quality=95)
    print(f"written {OUT_JPG} ({pix.width}x{pix.height})")


if __name__ == "__main__":
    main()
