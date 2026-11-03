#!/usr/bin/env python3
"""
Builds docs/CRUD_BY_MEMBER.pdf from docs/CRUD_BY_MEMBER.md.

Requires: pip install markdown weasyprint beautifulsoup4
Run from the project root:  python3 scripts/build-crud-pdf.py
"""
import re
from pathlib import Path

import markdown
from bs4 import BeautifulSoup, Tag
from weasyprint import HTML

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "CRUD_BY_MEMBER.md"
OUT = ROOT / "docs" / "CRUD_BY_MEMBER.pdf"

# Student IDs for each team member (member number -> ID)
MEMBER_IDS = {
    1: "IT26100914",
    2: "IT26100915",
    3: "IT26100916",
    4: "IT26100918",
    5: "IT26100919",
    6: "IT26100920",
}

# Component name for the cover cards (taken from the document headings)
MEMBER_SHORT = {
    1: "User & Authentication",
    2: "Movie Catalogue",
    3: "Theatre & Screen",
    4: "Showtime Scheduling",
    5: "Booking & Seat Reservation",
    6: "Payment & File Handling",
}

OP_LABELS = {
    "Create": ("CREATE", "op-create"),
    "Read": ("READ", "op-read"),
    "Update": ("UPDATE", "op-update"),
    "Delete": ("DELETE", "op-delete"),
    "Write": ("WRITE", "op-write"),
}

ROW_LABELS = {"Endpoint", "What it does", "Rules", "Why"}

CSS = """
@page { size: A4; margin: 22mm 16mm 18mm 16mm;
        @top-left { content: "SE1020 · CRUD by Team Member"; font-size: 8pt; color: #6b7280; }
        @top-right { content: "Movie Ticket Booking System"; font-size: 8pt; color: #6b7280; }
        @bottom-right { content: counter(page) " / " counter(pages); font-size: 8pt; color: #6b7280; } }
@page :first { margin: 0; @top-left { content: none; } @top-right { content: none; } @bottom-right { content: none; } }

body { font-family: 'DejaVu Sans', sans-serif; font-size: 9.5pt; line-height: 1.45; color: #1f2937; margin: 0; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 8pt; background: #f1f5f9; color: #334155; padding: 1pt 4pt; border-radius: 3pt; }

/* ---------- Cover ---------- */
.cover { width: 210mm; height: 297mm; padding: 0; margin: 0;
         background: linear-gradient(160deg, #1e1b4b 0%, #4c1d95 45%, #be185d 100%); color: #fff; position: relative; }
.cover-inner { padding: 22mm 18mm 0 18mm; }
.cover .kicker { font-size: 9pt; letter-spacing: 2pt; text-transform: uppercase; color: #f9a8d4; }
.cover h1 { font-size: 34pt; line-height: 1.1; margin: 8pt 0 6pt 0; color: #fff; }
.cover .sub { font-size: 11pt; color: #e9d5ff; margin: 0 0 18pt 0; }
.cover .ticket { margin-top: 4pt; }
.cover .rule { border-top: 1px solid rgba(255,255,255,0.35); margin: 16pt 0 14pt 0; }
.cover .label { font-size: 8pt; letter-spacing: 1.5pt; text-transform: uppercase; color: #c4b5fd; margin-bottom: 6pt; }

table.grid { width: 100%; border-collapse: separate; border-spacing: 8pt; margin: 0 -8pt; }
table.grid td { width: 33%; vertical-align: top; }
.mcard { background: #ffffff; color: #1f2937; border-radius: 10pt; padding: 10pt 11pt 11pt 11pt; border-top: 5pt solid #7c3aed; }
.mcard .num { font-size: 7.5pt; letter-spacing: 1.2pt; text-transform: uppercase; color: #6b7280; }
.mcard .id { font-size: 13pt; font-weight: bold; margin: 2pt 0 4pt 0; color: #111827; }
.mcard .name { font-size: 8.5pt; color: #374151; }
.mcard .count { font-size: 7.5pt; color: #6b7280; margin-top: 5pt; }

.cover .foot { position: absolute; bottom: 14mm; left: 18mm; right: 18mm; font-size: 8pt; color: #c4b5fd; }

/* ---------- Member sections ---------- */
.member { margin-top: 6pt; }
.member-head { border-radius: 8pt; color: #fff; padding: 9pt 13pt; margin: 0 0 8pt 0; }
.member-head .mnum { font-size: 7.5pt; letter-spacing: 1.5pt; text-transform: uppercase; opacity: 0.9; }
.member-head .mtitle { font-size: 15pt; font-weight: bold; margin-top: 1pt; }
.member-head .mid { font-size: 9.5pt; font-weight: bold; text-align: right; }
.member-head .mid span { display: inline-block; background: rgba(255,255,255,0.22); padding: 3pt 8pt; border-radius: 6pt; }

h3 { font-size: 10.5pt; text-transform: uppercase; letter-spacing: 1pt; margin: 14pt 0 6pt 0; padding-bottom: 3pt; border-bottom: 1px solid #e5e7eb; }

/* Member accent colours */
.m1 .member-head { background: #6d28d9; }  .m1 h3 { color: #6d28d9; }
.m2 .member-head { background: #be185d; }  .m2 h3 { color: #be185d; }
.m3 .member-head { background: #0e7490; }  .m3 h3 { color: #0e7490; }
.m4 .member-head { background: #4338ca; }  .m4 h3 { color: #4338ca; }
.m5 .member-head { background: #0f766e; }  .m5 h3 { color: #0f766e; }
.m6 .member-head { background: #9a3412; }  .m6 h3 { color: #9a3412; }

/* Operation cards */
.op { border: 1px solid #e5e7eb; border-left: 4px solid #9ca3af; border-radius: 6pt; background: #ffffff; padding: 7pt 10pt 4pt 10pt; margin: 0 0 8pt 0; page-break-inside: avoid; }
.op-head { margin-bottom: 4pt; }
.badge { display: inline-block; font-size: 7pt; font-weight: bold; letter-spacing: 1pt; color: #fff; padding: 2pt 6pt; border-radius: 3pt; margin-right: 5pt; vertical-align: middle; }
.op-title { font-weight: bold; font-size: 10pt; color: #111827; vertical-align: middle; }
.op-create { border-left-color: #16a34a; } .op-create .badge { background: #16a34a; }
.op-read   { border-left-color: #2563eb; } .op-read .badge   { background: #2563eb; }
.op-update { border-left-color: #d97706; } .op-update .badge { background: #d97706; }
.op-delete { border-left-color: #dc2626; } .op-delete .badge { background: #dc2626; }
.op-write  { border-left-color: #475569; } .op-write .badge  { background: #475569; }

table.rows { width: 100%; border-collapse: collapse; }
table.rows td { vertical-align: top; padding: 2.5pt 0; font-size: 9pt; }
table.rows td.lbl { width: 22%; font-size: 7.5pt; text-transform: uppercase; letter-spacing: 0.6pt; font-weight: bold; color: #6b7280; padding-top: 3.2pt; }
table.rows td.val { color: #1f2937; }
table.rows ul { margin: 1pt 0 0 12pt; padding: 0; }
table.rows li { margin-bottom: 1.5pt; }

p { margin: 4pt 0 6pt 0; }
ul { margin: 2pt 0 6pt 14pt; padding: 0; }
li { margin-bottom: 2pt; }
hr { display: none; }

/* Summary */
h2.summary-title { font-size: 16pt; color: #111827; margin: 0 0 8pt 0; page-break-before: always; }
table.summary tr { page-break-inside: avoid; }
table.summary { width: 100%; border-collapse: collapse; font-size: 8.5pt; }
table.summary th { background: #111827; color: #fff; text-align: left; padding: 6pt; }
table.summary td { border-bottom: 1px solid #e5e7eb; padding: 6pt; vertical-align: top; }
table.summary tr:nth-child(even) td { background: #f9fafb; }
table.summary td.sid { font-weight: bold; white-space: nowrap; }
"""


def member_card(num: int, ops: int) -> str:
    return (
        f'<div class="mcard" style="border-top-color:{MEMBER_COLORS[num]}">'
        f'<div class="num">Member {num}</div>'
        f'<div class="id">{MEMBER_IDS[num]}</div>'
        f'<div class="name">{MEMBER_SHORT[num]}</div>'
        f'<div class="count">{ops} CRUD operations</div></div>'
    )


MEMBER_COLORS = {1: "#a78bfa", 2: "#f472b6", 3: "#22d3ee", 4: "#818cf8", 5: "#2dd4bf", 6: "#fb923c"}


def cover_html(op_counts: dict) -> str:
    cards = [member_card(n, op_counts.get(n, 0)) for n in range(1, 7)]
    rows = []
    for i in range(0, 6, 3):
        cells = "".join(f"<td>{c}</td>" for c in cards[i:i + 3])
        rows.append(f"<tr>{cells}</tr>")
    ticket_svg = (
        '<svg width="46" height="32" viewBox="0 0 46 32" xmlns="http://www.w3.org/2000/svg">'
        '<rect x="1" y="1" width="44" height="30" rx="5" fill="#f9a8d4"/>'
        '<circle cx="1" cy="16" r="4" fill="#4c1d95"/><circle cx="45" cy="16" r="4" fill="#4c1d95"/>'
        '<line x1="32" y1="5" x2="32" y2="27" stroke="#4c1d95" stroke-width="1.5" stroke-dasharray="3 3"/>'
        '<rect x="7" y="10" width="20" height="3" rx="1.5" fill="#4c1d95"/>'
        '<rect x="7" y="17" width="14" height="3" rx="1.5" fill="#4c1d95"/></svg>'
    )
    return f"""
<div class="cover">
  <div class="cover-inner">
    <table style="width:100%"><tr>
      <td style="width:52pt">{ticket_svg}</td>
      <td class="kicker" style="vertical-align:middle">SE1020 · Object Oriented Programming Project</td>
    </tr></table>
    <h1 style="margin-top:22pt">CRUD by<br/>Team Member</h1>
    <p class="sub">Movie Ticket Booking System · Java Spring Boot, SQL database and HTML user interface</p>
    <div class="rule"></div>
    <div class="label">Team members and student IDs</div>
    <table class="grid">{''.join(rows)}</table>
    <div class="rule" style="margin-top:20pt"></div>
    <p style="font-size:8.5pt;color:#ddd6fe;margin:0">Each member owns one component and performs Create, Read, Update and Delete operations.
    Customer and admin operations are listed separately.</p>
  </div>
  <div class="foot">Generated from docs/CRUD_BY_MEMBER.md</div>
</div>
"""


def text_of(tag: Tag) -> str:
    return tag.get_text(" ", strip=True)


def make_op_card(soup: BeautifulSoup, kind_word: str, title: str, endpoint_nodes, body_nodes) -> Tag:
    label, cls = OP_LABELS[kind_word]
    card = soup.new_tag("div", attrs={"class": f"op {cls}"})
    head = soup.new_tag("div", attrs={"class": "op-head"})
    badge = soup.new_tag("span", attrs={"class": "badge"})
    badge.string = label
    head.append(badge)
    t = soup.new_tag("span", attrs={"class": "op-title"})
    t.string = title
    head.append(t)
    for node in endpoint_nodes:
        head.append(" ")
        head.append(node)
    card.append(head)
    for node in body_nodes:
        card.append(node)
    return card


def build_rows_table(soup: BeautifulSoup, items: list) -> Tag:
    """items: list of (label, [child nodes]) tuples."""
    table = soup.new_tag("table", attrs={"class": "rows"})
    for label, nodes in items:
        tr = soup.new_tag("tr")
        td_v = soup.new_tag("td", attrs={"class": "val"})
        for n in nodes:
            td_v.append(n)
        if label:
            td_l = soup.new_tag("td", attrs={"class": "lbl"})
            td_l.string = label
            tr.append(td_l)
        else:
            td_v["colspan"] = "2"  # unlabelled continuation line spans both columns
        tr.append(td_v)
        table.append(tr)
    return table


def transform_op_paragraph(soup: BeautifulSoup, p: Tag, count: list) -> Tag | None:
    strong = p.find("strong")
    if not strong:
        return None
    first = text_of(strong).split(" ")[0]
    if first not in OP_LABELS:
        return None
    title = text_of(strong)
    endpoint = [c.extract() for c in p.find_all("code")]
    # remove the bold title from the paragraph before anything else is taken
    strong.extract()
    # the next sibling list becomes the card body
    body = []
    nxt = p.find_next_sibling()
    if nxt is not None and nxt.name in ("ul", "ol"):
        body.append(nxt.extract())
    card = make_op_card(soup, first, title, endpoint, [])
    rows = []
    if body:
        for li in body[0].find_all("li", recursive=False):
            lead = li.find("strong")
            lead_text = text_of(lead).rstrip(":") if lead else ""
            if lead and lead_text in ROW_LABELS:
                lead.extract()
                nodes = [c.extract() for c in list(li.contents)]
                rows.append((lead_text, nodes))
            else:
                label = lead_text if lead else ""
                rows.append((label, [c.extract() for c in list(li.contents)]))
        card.append(build_rows_table(soup, rows))
    count[0] += 1
    p.insert_before(card)
    p.decompose()
    if body:
        body[0].decompose()
    return card


def transform_op_list_item(soup: BeautifulSoup, li: Tag, count: list) -> Tag | None:
    strong = li.find("strong")
    if not strong:
        return None
    first = text_of(strong).split(" ")[0]
    if first not in OP_LABELS:
        return None
    title = text_of(strong)
    strong.extract()
    endpoint = [c.extract() for c in li.find_all("code")]
    # remaining text after the title (usually a dash-prefixed description)
    text_nodes = [c.extract() for c in list(li.contents)]
    card = make_op_card(soup, first, title, endpoint, [])
    rows = [("What it does", text_nodes)]
    card.append(build_rows_table(soup, rows))
    count[0] += 1
    return card


def transform(html: str):
    soup = BeautifulSoup(html, "html.parser")
    op_counts = {}
    count = [0]

    # 1. Convert the member headings into coloured banners and wrap each member's content
    for h2 in list(soup.find_all("h2")):
        text = text_of(h2)
        m = re.match(r"Member (\d)\s+[–-]\s+(.*)", text)
        if not m:
            continue
        num = int(m.group(1))
        title = m.group(2).strip()
        section = soup.new_tag("div", attrs={"class": f"member m{num}"})
        banner = soup.new_tag("div", attrs={"class": "member-head"})
        banner.append(BeautifulSoup(
            f'<table style="width:100%"><tr>'
            f'<td><div class="mnum">Member {num}</div><div class="mtitle">{title}</div></td>'
            f'<td class="mid" style="vertical-align:middle"><span>{MEMBER_IDS[num]}</span></td>'
            f'</tr></table>', "html.parser"))
        section.append(banner)
        # move siblings until the next h2 into the section
        sibling = h2.next_sibling
        h2.insert_before(section)
        while sibling is not None and not (isinstance(sibling, Tag) and sibling.name == "h2"):
            nxt = sibling.next_sibling
            if isinstance(sibling, Tag) and sibling.name == "hr":
                sibling.decompose()
            elif isinstance(sibling, Tag):
                section.append(sibling.extract())
            sibling = nxt
        h2.decompose()
        op_counts[num] = 0
        # Attach the section's counter so the transform below can count ops per member
        section["data-member"] = str(num)

    # 2. Convert operation paragraphs and list items into operation cards
    for section in soup.find_all("div", class_="member"):
        num = int(section["data-member"])
        before = count[0]
        for p in list(section.find_all("p")):
            if p.parent is None or p.find_parent("table") is not None:
                continue
            transform_op_paragraph(soup, p, count)
        for li in list(section.find_all("li")):
            if li.parent is None or li.find_parent("table") is not None:
                continue
            strong = li.find("strong")
            if not strong or text_of(strong).split(" ")[0] not in ("Read", "Write"):
                continue
            card = transform_op_list_item(soup, li, count)
            if card is not None:
                parent_list = li.parent
                parent_list.insert_before(card)
                li.decompose()
                if not parent_list.find_all("li"):
                    parent_list.decompose()
        op_counts[num] = count[0] - before

    # 3. Summary table: add IDs to the Member column
    for table in soup.find_all("table"):
        ths = [text_of(th) for th in table.find_all("th")]
        if not ths or ths[0] != "Member":
            continue
        table["class"] = ["summary"]
        for tr in table.find_all("tr")[1:]:
            tds = tr.find_all("td")
            if len(tds) < 2:
                continue
            try:
                num = int(text_of(tds[0]))
            except ValueError:
                continue
            # First column: "Member 1" plus the student ID on the next line
            tds[0].clear()
            tds[0].append(BeautifulSoup(
                f'<b>Member {num}</b><br/><span style="color:#6b7280">{MEMBER_IDS[num]}</span>',
                "html.parser"))
            tds[0]["class"] = ["sid"]
    # Summary heading
    for h2 in soup.find_all("h2"):
        if text_of(h2) == "Summary":
            h2["class"] = ["summary-title"]

    return soup, op_counts


def main():
    md_text = SRC.read_text(encoding="utf-8")
    body_html = markdown.markdown(md_text, extensions=["tables"])
    soup, op_counts = transform(body_html)
    # the summary table: keep a single ID column (the ID is in the "Member" cell above)
    html = f"""<!doctype html><html><head><meta charset="utf-8">
<style>{CSS}</style></head><body>
{cover_html(op_counts)}
<div style="page-break-before: always"></div>
{soup}
</body></html>"""
    HTML(string=html, base_url=str(ROOT / "docs")).write_pdf(str(OUT))
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
