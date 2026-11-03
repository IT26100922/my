#!/usr/bin/env python3
"""
Builds the progress presentation (16:9 PDF) for the Movie Ticket Booking project.
Output: docs/presentation/MovieTicket_Progress_Presentation.pdf

Requires: pip install weasyprint
Run from the project root:  python3 scripts/build-presentation.py
"""
import argparse
import re
from pathlib import Path

from weasyprint import HTML

ROOT = Path(__file__).resolve().parent.parent
PRES = ROOT / "docs" / "presentation"
OUT = PRES / "MovieTicket_Progress_Presentation.pdf"
OUT_DARK = PRES / "MovieTicket_Progress_Presentation_Dark.pdf"

CSS = """
@page { size: 13.333in 7.5in; margin: 0; }
* { box-sizing: border-box; }
body { margin: 0; font-family: 'DejaVu Sans', sans-serif; color: #ffffff; }
.slide { width: 13.333in; height: 7.5in; position: relative; overflow: hidden; page-break-after: always;
         background: linear-gradient(135deg, #040d26 0%, #0b2a6b 55%, #1e5bd6 100%); }
.slide:last-child { page-break-after: auto; }
.brand { position: absolute; top: 0.35in; left: 0.7in; font-size: 9pt; letter-spacing: 1.6pt; text-transform: uppercase; color: #93c5fd; }
.pno { position: absolute; bottom: 0.3in; right: 0.6in; font-size: 9pt; color: #93c5fd; }
.kicker { position: absolute; top: 0.7in; left: 0.7in; font-size: 11pt; letter-spacing: 2.2pt; text-transform: uppercase; color: #7dd3fc; }
h1 { position: absolute; top: 0.95in; left: 0.7in; margin: 0; font-size: 36pt; font-weight: bold; color: #ffffff; }
.lead { position: absolute; top: 1.95in; left: 0.7in; right: 0.7in; font-size: 14pt; color: #cbd5e1; }
.card { position: absolute; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.16); border-radius: 12pt; padding: 14pt 16pt; }
.card h3 { margin: 0 0 6pt 0; font-size: 15pt; color: #ffffff; }
.card p, .card li { font-size: 11.5pt; line-height: 1.4; color: #dbeafe; }
.card ul { margin: 0; padding-left: 16pt; }
.label { font-size: 9pt; letter-spacing: 1.4pt; text-transform: uppercase; font-weight: bold; }
.num { display: inline-block; width: 26pt; height: 26pt; line-height: 26pt; border-radius: 13pt; text-align: center; font-weight: bold; font-size: 11pt; color: #0b1a3d; background: #7dd3fc; }
.chip { display: inline-block; width: 22pt; height: 18pt; line-height: 18pt; border-radius: 4pt; text-align: center; font-size: 9pt; font-weight: bold; color: #ffffff; margin-right: 6pt; }
.c { background: #22c55e; } .r { background: #3b82f6; } .u { background: #f59e0b; } .d { background: #ef4444; } .f { background: #64748b; }
.muted { color: #94a3b8; }
.stat { position: absolute; text-align: left; }
.stat .big { font-size: 40pt; font-weight: bold; color: #7dd3fc; }
.stat .small { font-size: 11pt; color: #cbd5e1; margin-top: 2pt; }
table.plain { border-collapse: collapse; width: 100%; font-size: 11pt; }
table.plain th { text-align: left; color: #7dd3fc; font-size: 9pt; letter-spacing: 1pt; text-transform: uppercase; padding: 6pt 8pt; border-bottom: 1px solid rgba(255,255,255,0.25); }
table.plain td { padding: 7pt 8pt; border-bottom: 1px solid rgba(255,255,255,0.10); color: #e2e8f0; }
.box { position: absolute; border-radius: 10pt; padding: 10pt 14pt; }
"""

MEMBERS = [
    # n, id, component, package, tables, accent
    (1, "IT26100914", "User & Authentication", "user", "users", "#a78bfa"),
    (2, "IT26100915", "Movie Catalogue", "movie", "movies", "#f472b6"),
    (3, "IT26100916", "Theatre & Screen", "theatre", "theatres, screens", "#22d3ee"),
    (4, "IT26100918", "Showtime Scheduling", "show", "showtimes", "#818cf8"),
    (5, "IT26100919", "Booking & Seat Reservation", "booking", "bookings, booking_seats", "#2dd4bf"),
    (6, "IT26100920", "Payment & File Handling", "payment, file", "payments", "#fb923c"),
]

# Operation counts per member (from docs/CRUD_BY_MEMBER.md): create, read, update, delete, file
OPS = {1: (2, 4, 2, 3, 0), 2: (1, 2, 1, 1, 0), 3: (2, 2, 2, 2, 0),
       4: (1, 2, 1, 1, 0), 5: (1, 2, 1, 2, 0), 6: (1, 4, 1, 1, 1)}
OP_COLORS = ["#22c55e", "#3b82f6", "#f59e0b", "#ef4444", "#64748b"]
OP_NAMES = ["Create", "Read", "Update", "Delete", "File (write)"]


def slide(inner: str, n: int) -> str:
    return f'<section class="slide">{inner}<div class="pno">{n:02d}</div></section>'


def brand_and_title(kicker: str, title: str, lead: str = "") -> str:
    out = f'<div class="brand">CineMax · Movie Ticket Booking System</div>'
    out += f'<div class="kicker">{kicker}</div><h1>{title}</h1>'
    if lead:
        out += f'<div class="lead">{lead}</div>'
    return out


def s_cover() -> str:
    return f"""
<section class="slide" style="background:#040d26 url(assets/cover.jpg) no-repeat center / cover;">
  <div style="position:absolute;left:0;top:0;width:7.6in;height:7.5in;background:linear-gradient(90deg, rgba(4,13,38,0.92) 0%, rgba(4,13,38,0.55) 70%, rgba(4,13,38,0) 100%);"></div>
  <div style="position:absolute;left:0.8in;top:1.25in;width:7in;">
    <div class="label" style="color:#7dd3fc;letter-spacing:2.4pt;">SE1020 · Progress Presentation</div>
    <div style="font-size:70pt;font-weight:bold;margin-top:14pt;line-height:1;">CineMax</div>
    <div style="font-size:26pt;color:#e0f2fe;margin-top:8pt;">Movie Ticket Booking System</div>
    <div style="width:1.1in;height:4pt;background:#38bdf8;margin:18pt 0 16pt 0;"></div>
    <div style="font-size:13pt;color:#cbd5e1;line-height:1.5;">Java 17 · Spring Boot 3.3.5 · SQL database<br/>HTML, CSS and JavaScript user interface</div>
  </div>
  <div class="pno" style="color:#ffffff;">01</div>
</section>"""


def s_crew() -> str:
    cards = ""
    for i, (n, sid, comp, pkg, tables, accent) in enumerate(MEMBERS):
        col, row = i % 3, i // 3
        x = 0.7 + col * 4.1
        y = 2.75 + row * 2.2
        cards += f"""
        <div class="card" style="left:{x}in;top:{y}in;width:3.8in;height:1.95in;border-top:5pt solid {accent};">
          <div class="label" style="color:{accent};">Member {n}</div>
          <div style="font-size:22pt;font-weight:bold;margin-top:6pt;">{sid}</div>
          <div style="font-size:12.5pt;color:#e2e8f0;margin-top:6pt;">{comp}</div>
          <div style="font-size:10pt;color:#94a3b8;margin-top:10pt;">Package: <b style="color:#cbd5e1;">{pkg}</b></div>
        </div>"""
    inner = brand_and_title("Team", "Crew Members", "Six members, one component each. Every member owns the entity, DTO, repository, service and controller for their part.")
    return slide(inner + cards, 2)


def s_overview() -> str:
    pillars = [
        ("Customers", ["Register and log in", "Browse movies and showtimes", "Choose seats on a seat map", "Pay, view and cancel bookings"], "#38bdf8"),
        ("Admins", ["Separate admin sign-in", "Manage movies, theatres, screens", "Schedule showtimes and bookings", "Record refunds and export reports"], "#f472b6"),
        ("The system", ["Spring Boot REST API", "MySQL 8 database", "Static HTML, CSS and JS pages", "Seed data loaded from text files"], "#a3e635"),
    ]
    inner = brand_and_title("Project overview", "What we are building", "An online booking system for CineMax cinemas (Negombo and Colombo). Customers reserve seats; admins run the catalogue and the schedule.")
    x = 0.7
    for title, items, accent in pillars:
        lis = "".join(f"<li>{it}</li>" for it in items)
        inner += f"""
        <div class="card" style="left:{x}in;top:3.0in;width:3.9in;height:2.75in;border-top:4pt solid {accent};">
          <h3 style="color:{accent};">{title}</h3>
          <ul>{lis}</ul>
        </div>"""
        x += 4.15
    stats = [("6", "components with CRUD"), ("43", "CRUD operations in total"), ("2", "separate login portals"), ("57/57", "API smoke tests passing")]
    x = 0.7
    for big, small in stats:
        inner += f'<div class="stat" style="left:{x}in;top:5.95in;width:2.9in;"><div class="big">{big}</div><div class="small">{small}</div></div>'
        x += 3.05
    return slide(inner, 3)


def s_workload() -> str:
    rows = ""
    for n, sid, comp, pkg, tables, accent in MEMBERS:
        rows += f"""<tr>
          <td><span class="label" style="color:{accent};">M{n}</span></td>
          <td style="font-weight:bold;">{sid}</td><td>{comp}</td><td class="muted">{pkg}</td><td class="muted">{tables}</td></tr>"""
    table = f"""
    <div class="card" style="left:0.7in;top:2.2in;width:7.1in;height:4.7in;padding:12pt 14pt;">
      <table class="plain">
        <tr><th>Member</th><th>Student ID</th><th>Component</th><th>Package</th><th>Tables</th></tr>
        {rows}
      </table>
    </div>"""
    # stacked bar chart of operations per member
    unit = 26
    bars = ""
    for i, (n, sid, comp, pkg, tables, accent) in enumerate(MEMBERS):
        y = 40 + i * 52
        x = 0
        seg = ""
        for k, val in enumerate(OPS[n]):
            if val == 0:
                continue
            w = val * unit
            seg += f'<rect x="{x}" y="{y}" width="{w}" height="30" fill="{OP_COLORS[k]}"/>'
            seg += f'<text x="{x + w/2}" y="{y + 20}" text-anchor="middle" font-size="13" font-weight="bold" fill="#ffffff">{val}</text>'
            x += w
        total = sum(OPS[n])
        bars += seg + f'<text x="{x + 8}" y="{y + 21}" font-size="14" font-weight="bold" fill="#e0f2fe">{total}</text>'
        bars += f'<text x="0" y="{y - 6}" font-size="11" fill="#cbd5e1">M{n} · {sid}</text>'
    legend = "".join(
        f'<span style="display:inline-block;margin-right:12pt;font-size:10pt;color:#cbd5e1;">'
        f'<span style="display:inline-block;width:10pt;height:10pt;background:{OP_COLORS[k]};border-radius:2pt;margin-right:4pt;"></span>{OP_NAMES[k]}</span>'
        for k in range(5))
    chart = f"""
    <div class="card" style="left:8.1in;top:2.2in;width:4.55in;height:4.7in;padding:12pt 14pt;">
      <div class="label" style="color:#7dd3fc;">Operations per member</div>
      <svg width="4.2in" height="3.7in" viewBox="0 0 400 320" style="margin-top:6pt;">{bars}</svg>
      <div style="margin-top:4pt;">{legend}</div>
    </div>"""
    inner = brand_and_title("Workload distribution", "Who does what")
    return slide(inner + table + chart, 4)


def s_crud() -> str:
    ops_text = {
        1: [("c", "Customer register; admin adds admin"), ("r", "Own profile; customer and admin lists"),
            ("u", "Own profile; admin role change"), ("d", "Own account; customer (no bookings); admin")],
        2: [("c", "Admin adds movie"), ("r", "Movie list and details"),
            ("u", "Admin edits movie (format fixed)"), ("d", "Admin deletes movie (no showtimes)")],
        3: [("c", "Admin adds theatre and screen"), ("r", "Theatre and screen lists"),
            ("u", "Admin edits (screen type fixed)"), ("d", "Theatre (no screens), screen (no shows)")],
        4: [("c", "Admin adds showtime, overlap check"), ("r", "Public list and admin list"),
            ("u", "Admin edits showtime"), ("d", "Admin deletes showtime (no bookings)")],
        5: [("c", "Customer books seats"), ("r", "My bookings; all bookings (admin)"),
            ("u", "Change seats while pending payment"), ("d", "Cancel with refund; admin deletes cancelled")],
        6: [("c", "Customer pays (card, wallet, cash)"), ("r", "My payments; all payments (admin)"),
            ("u", "Admin records manual refund"), ("d", "Admin deletes refunded payment"), ("f", "Import movies and users; export sales")],
    }
    cards = ""
    for i, (n, sid, comp, pkg, tables, accent) in enumerate(MEMBERS):
        col, row = i % 3, i // 3
        x = 0.7 + col * 4.1
        y = 2.0 + row * 2.6
        items = ""
        for key, text in ops_text[n]:
            label = {"c": "C", "r": "R", "u": "U", "d": "D", "f": "F"}[key]
            items += f'<div style="margin-bottom:4pt;font-size:10.5pt;color:#e2e8f0;"><span class="chip {key}">{label}</span>{text}</div>'
        cards += f"""
        <div class="card" style="left:{x}in;top:{y}in;width:3.8in;height:2.42in;border-top:4pt solid {accent};padding:10pt 13pt;">
          <div class="label" style="color:{accent};">Member {n} · {sid}</div>
          <div style="font-size:13pt;font-weight:bold;margin:3pt 0 6pt 0;">{comp}</div>
          {items}
        </div>"""
    inner = brand_and_title("CRUD for every member", "Create, Read, Update, Delete")
    return slide(inner + cards, 5)


def s_layers() -> str:
    layers = [
        ("Pages", "Static HTML, CSS and JavaScript pages", "#38bdf8"),
        ("Controllers", "REST endpoints, form validation, login and role checks", "#60a5fa"),
        ("Services", "Business rules: seats, pricing, refunds, payment checks", "#818cf8"),
        ("Repositories", "Spring Data JPA queries, one per entity", "#a78bfa"),
        ("Database", "MySQL 8, schema created automatically", "#f472b6"),
    ]
    inner = brand_and_title("How the system is layered", "Layers and packages")
    y = 2.1
    for name, desc, accent in layers:
        inner += f"""
        <div class="box" style="left:0.7in;top:{y}in;width:8.2in;height:0.8in;background:linear-gradient(90deg, rgba(255,255,255,0.14), rgba(255,255,255,0.05));border-left:6pt solid {accent};padding-top:14pt;">
          <span style="font-size:17pt;font-weight:bold;display:inline-block;width:2.4in;vertical-align:middle;">{name}</span><span style="font-size:12.5pt;color:#dbeafe;display:inline-block;width:5.3in;vertical-align:middle;">{desc}</span>
        </div>"""
        y += 1.0
    pkg = """
    <div class="card" style="left:9.3in;top:2.1in;width:3.35in;height:4.6in;">
      <div class="label" style="color:#7dd3fc;">Packages per component</div>
      <div style="font-size:11pt;color:#dbeafe;line-height:1.7;margin-top:8pt;">
        <b>entity</b> · JPA classes<br/><b>dto</b> · request and response records<br/>
        <b>repository</b> · Spring Data interfaces<br/><b>service</b> · business rules<br/>
        <b>controller</b> · REST endpoints<br/><br/>
        <span class="muted">Each of the 6 components has its own set of these packages.</span>
      </div>
    </div>"""
    return slide(inner + pkg, 6)


def s_oop() -> str:
    concepts = [
        ("Inheritance", "User is the parent of Customer and Admin. Movie is extended by Standard, IMAX and 3D. Screen by Standard, Premium and VIP.", "#38bdf8"),
        ("Abstraction", "Interfaces define the contract: PricingStrategy, CancellationPolicy, PaymentProcessor and AuthStrategy. Services use the interface, not the class.", "#f472b6"),
        ("Polymorphism", "Each movie format gives its own surcharge and each screen type its own multiplier. The right version runs without an if-chain.", "#a3e635"),
        ("Encapsulation", "Fields are private and set through methods. For example, a movie duration under 30 minutes is rejected by the setter.", "#fb923c"),
        ("Information hiding", "Response DTOs never send the password hash. Payment cards store only the last four digits.", "#c084fc"),
    ]
    inner = brand_and_title("OOP concepts in the code", "Five OOP principles, from the project")
    x = 0.7
    for title, desc, accent in concepts:
        inner += f"""
        <div class="card" style="left:{x}in;top:2.1in;width:2.3in;height:4.7in;border-top:5pt solid {accent};">
          <div class="num" style="background:{accent};">{concepts.index((title, desc, accent)) + 1}</div>
          <h3 style="margin-top:12pt;color:{accent};font-size:14pt;">{title}</h3>
          <p>{desc}</p>
        </div>"""
        x += 2.45
    return slide(inner, 7)


def s_rules() -> str:
    rules = [
        ("Seats", "Maximum 10 seats per booking. A seat held by a pending or confirmed booking cannot be taken. Rows are letters (max 26), seats per row up to 30."),
        ("Showtimes", "Start time must be in the future. Two shows cannot overlap on the same screen. Deleting a showtime is blocked once bookings exist."),
        ("Pricing", "Base price × screen multiplier + format surcharge (IMAX +LKR 300, 3D +LKR 200). Then the highest-priority time rule applies."),
        ("Refunds", "Cancel 24 h or more before the show: 100%. Between 2 and 24 h: 50%. Under 2 h: no refund."),
        ("Payments", "Card: 16 digits with a 2% fee. Wallet: ID of 5 to 40 characters, no fee. Cash counter: code CNT-NN, no fee."),
        ("Access", "Separate customer and admin logins. Every /api/admin route requires the ADMIN role. Passwords are stored as BCrypt hashes."),
    ]
    inner = brand_and_title("Business rules", "Rules the system enforces")
    for i, (title, desc) in enumerate(rules):
        col, row = i % 3, i // 3
        x = 0.7 + col * 4.1
        y = 2.1 + row * 2.45
        inner += f"""
        <div class="card" style="left:{x}in;top:{y}in;width:3.8in;height:2.2in;">
          <span class="num">{i + 1}</span>
          <h3 style="display:inline-block;margin:0 0 0 10pt;vertical-align:middle;font-size:16pt;">{title}</h3>
          <p style="margin-top:12pt;">{desc}</p>
        </div>"""
    return slide(inner, 8)


def s_pricing() -> str:
    data = [("Standard", 1500, 1800), ("Premium", 1950, 2340), ("VIP", 2700, 3240)]
    base_y, scale = 292, 210 / 3300
    bars = ""
    for i, (name, wd, we) in enumerate(data):
        cx = 110 + i * 170
        for j, (v, color) in enumerate([(wd, "#38bdf8"), (we, "#f59e0b")]):
            h = v * scale
            x = cx - 50 + j * 56
            bars += f'<rect x="{x}" y="{base_y - h:.1f}" width="50" height="{h:.1f}" rx="4" fill="{color}"/>'
            bars += f'<text x="{x + 25}" y="{base_y - h - 8:.1f}" text-anchor="middle" font-size="13" font-weight="bold" fill="#ffffff">{v:,}</text>'
        bars += f'<text x="{cx + 2}" y="{base_y + 26}" text-anchor="middle" font-size="14" fill="#e0f2fe">{name}</text>'
    bars += f'<line x1="30" y1="{base_y}" x2="570" y2="{base_y}" stroke="#94a3b8" stroke-width="1"/>'
    legend = (
        '<span style="display:inline-block;margin-right:16pt;font-size:11pt;color:#cbd5e1;">'
        '<span style="display:inline-block;width:10pt;height:10pt;background:#38bdf8;border-radius:2pt;margin-right:5pt;"></span>Weekday</span>'
        '<span style="display:inline-block;font-size:11pt;color:#cbd5e1;">'
        '<span style="display:inline-block;width:10pt;height:10pt;background:#f59e0b;border-radius:2pt;margin-right:5pt;"></span>Weekend (×1.20)</span>')
    inner = brand_and_title("Pricing example", "Price per seat, 2D movie, base LKR 1,500")
    inner += f"""
    <div class="card" style="left:0.7in;top:2.1in;width:4.6in;height:4.8in;">
      <div class="label" style="color:#7dd3fc;">How the price is built</div>
      <ol style="font-size:12pt;color:#dbeafe;line-height:1.55;padding-left:18pt;margin-top:10pt;">
        <li>Base price: LKR 1,500</li>
        <li>× screen multiplier: Standard 1.00, Premium 1.30, VIP 1.80</li>
        <li>+ format surcharge: 2D 0, IMAX +300, 3D +200</li>
        <li>× time rule: weekday 1.00, weekend 1.20, matinee (before 12:00) 0.80</li>
      </ol>
      <p style="font-size:10.5pt;color:#94a3b8;margin-top:10pt;">If a show is both on a weekend and before 12:00, the matinee rule wins because it has the higher priority.</p>
    </div>
    <div class="card" style="left:5.7in;top:2.1in;width:6.95in;height:4.8in;">
      <div class="label" style="color:#7dd3fc;">Seat price (LKR)</div>
      <svg width="6.6in" height="3.9in" viewBox="0 0 600 350" style="margin-top:8pt;">{bars}</svg>
      <div style="margin-top:2pt;">{legend}</div>
    </div>"""
    return slide(inner, 9)


def s_journey() -> str:
    steps = [
        ("Customer registers and logs in", "Customer portal, own profile"),
        ("Picks a movie and showtime", "Catalogue and live showtime list"),
        ("Chooses seats on the seat map", "Held seats are blocked for others"),
        ("Pays by card, wallet or cash", "Booking confirmed, loyalty points added"),
        ("Admin sees it in the dashboard", "Bookings and payments sections"),
        ("Refund or sales report if needed", "Manual refund or exported report"),
    ]
    inner = brand_and_title("One booking, start to finish", "The customer journey")
    for i, (title, sub) in enumerate(steps):
        col, row = i % 3, i // 3
        x = 0.7 + col * 4.1
        y = 2.1 + row * 2.45
        inner += f"""
        <div class="card" style="left:{x}in;top:{y}in;width:3.8in;height:2.15in;">
          <div style="font-size:34pt;font-weight:bold;color:#7dd3fc;">{i + 1}</div>
          <div style="font-size:15pt;font-weight:bold;margin-top:4pt;">{title}</div>
          <div style="font-size:11pt;color:#cbd5e1;margin-top:8pt;">{sub}</div>
        </div>"""
    return slide(inner, 10)


def s_progress() -> str:
    done = [
        "Six components with full CRUD and separate customer and admin logins",
        "Customer pages: movies, seat booking, payments, my bookings, profile",
        "Admin dashboard with CRUD for every section",
        "File handling: import movies and users, export a sales report",
        "Seed data for movies, users, theatres, screens and showtimes",
        "Documentation: README, class diagram, CRUD guide, workload plan",
        "API smoke test: 57 of 57 checks passing",
        "Every flow tested end to end against MySQL",
        "Every page checked on desktop and on a phone",
        "Each member committed to Git from their own account",
        "Final report with screenshots and each member's contribution",
        "Demo rehearsed and viva preparation done",
    ]
    todo = [
        "Add reminder notifications for customers",
        "Finalize the project within two weeks",
        "Test with campus students after the project is finalized",
    ]
    inner = brand_and_title("Progress status", "Where we are now")
    dl = "".join(f'<li style="margin-bottom:5pt;">{t}</li>' for t in done)
    tl = "".join(f'<li style="margin-bottom:9pt;">{t}</li>' for t in todo)
    inner += f"""
    <div class="card" style="left:0.7in;top:2.1in;width:6.6in;height:4.85in;border-top:5pt solid #22c55e;">
      <div class="label" style="color:#86efac;">Completed</div>
      <ul style="font-size:11.5pt;color:#e2e8f0;line-height:1.35;padding-left:18pt;margin-top:10pt;">{dl}</ul>
    </div>
    <div class="card" style="left:7.55in;top:2.1in;width:5.1in;height:4.85in;border-top:5pt solid #f59e0b;">
      <div class="label" style="color:#fcd34d;">Remaining before submission</div>
      <ul style="font-size:13pt;color:#e2e8f0;line-height:1.4;padding-left:18pt;margin-top:12pt;">{tl}</ul>
      <div style="position:absolute;bottom:14pt;left:16pt;right:16pt;font-size:10.5pt;color:#94a3b8;">Target: finalize within two weeks, then test with campus students.</div>
    </div>"""
    return slide(inner, 11)


def s_thanks() -> str:
    ids = " · ".join(sid for _, sid, *_ in MEMBERS)
    inner = f"""
    <div style="position:absolute;left:0.8in;top:2.2in;width:11.5in;">
      <div class="label" style="color:#7dd3fc;letter-spacing:2.4pt;">SE1020 · Movie Ticket Booking System</div>
      <div style="font-size:72pt;font-weight:bold;margin-top:14pt;line-height:1;">Thank You</div>
      <div style="width:1.1in;height:4pt;background:#38bdf8;margin:22pt 0 18pt 0;"></div>
      <div style="font-size:14pt;color:#cbd5e1;">CineMax · Negombo and Colombo</div>
      <div style="font-size:11pt;color:#94a3b8;margin-top:24pt;">{ids}</div>
    </div>"""
    return slide(inner, 12)


# ---- Light glass theme (colour-only restyle; slide text is unchanged) ----
LIGHT_STEP1 = [
    ("linear-gradient(135deg, #040d26 0%, #0b2a6b 55%, #1e5bd6 100%)",
     "url(assets/glass-bg.jpg) center / cover no-repeat, #e8f0fb"),
    ("position: absolute; background: rgba(255,255,255,0.08);",
     "position: absolute; background: radial-gradient(ellipse at 22% 0%, rgba(255,255,255,0.70) 0%, rgba(255,255,255,0.25) 35%, rgba(255,255,255,0) 68%), radial-gradient(ellipse at 100% 100%, rgba(255,255,255,0.40) 0%, rgba(255,255,255,0) 60%), linear-gradient(165deg, rgba(255,255,255,0.62) 0%, rgba(255,255,255,0.20) 45%, rgba(255,255,255,0.40) 100%);"),
    ("border: 1px solid rgba(255,255,255,0.16); border-radius: 12pt;", "border: 1.2pt solid rgba(255,255,255,0.95); border-radius: 18pt;"),
    ("background: #7dd3fc; }", "background: #bae6fd; }"),
    ("font-family: 'DejaVu Sans', sans-serif; color: #ffffff; }", "font-family: 'DejaVu Sans', sans-serif; color: #0f172a; }"),
    ("font-size: 36pt; font-weight: bold; color: #ffffff; }", "font-size: 36pt; font-weight: bold; color: #0f172a; }"),
    ("font-size: 15pt; color: #ffffff; }", "font-size: 15pt; color: #0f172a; }"),
]

# Single-pass colour map: hex/rgba tokens -> light-theme equivalents
LIGHT_MAP = {
    "#040d26": "#eef4ff",
    "#7dd3fc": "#1d4ed8",   # kickers, big stats, table headers
    "#93c5fd": "#2563eb",   # brand line, page numbers
    "#e0f2fe": "#0f172a",
    "#cbd5e1": "#334155",
    "#dbeafe": "#1e293b",
    "#e2e8f0": "#1e293b",
    "#94a3b8": "#475569",
    "#38bdf8": "#0284c7",
    "#fcd34d": "#b45309",
    "#86efac": "#15803d",
    "#a3e635": "#4d7c0f",
    "#22d3ee": "#0e7490",
    "#f472b6": "#db2777",
    "#a78bfa": "#7c3aed",
    "#818cf8": "#4f46e5",
    "#2dd4bf": "#0f766e",
    "#fb923c": "#ea580c",
    "#c084fc": "#9333ea",
    "#60a5fa": "#2563eb",
    "rgba(4,13,38,0.92)": "rgba(240,246,255,0.94)",
    "rgba(4,13,38,0.55)": "rgba(240,246,255,0.60)",
    "rgba(4,13,38,0)": "rgba(240,246,255,0)",
    "rgba(255,255,255,0.08)": "rgba(255,255,255,0.62)",   # glass card fill
    "rgba(255,255,255,0.16)": "rgba(255,255,255,1)",   # glass card edge
    "rgba(255,255,255,0.14)": "rgba(255,255,255,0.80)",
    "rgba(255,255,255,0.05)": "rgba(255,255,255,0.45)",
    "rgba(255,255,255,0.25)": "rgba(15,23,42,0.22)",      # table header rule
    "rgba(255,255,255,0.10)": "rgba(15,23,42,0.10)",      # table row rule
}


def to_light(html: str) -> str:
    for old, new in LIGHT_STEP1:
        html = html.replace(old, new)
    # pricing value labels sit on the slide background, so they need dark text
    html = re.sub(r'fill="#ffffff">(\d{1,3},\d{3})</text>', r'fill="#0f172a">\1</text>', html)
    html = re.sub(r"#[0-9a-fA-F]{6}\b|rgba\([^)]*\)",
                  lambda m: LIGHT_MAP.get(m.group(0).lower(), m.group(0)), html)
    return html


def build(theme: str) -> Path:
    body = "".join([
        s_cover(), s_crew(), s_overview(), s_workload(), s_crud(), s_layers(),
        s_oop(), s_rules(), s_pricing(), s_journey(), s_progress(), s_thanks(),
    ])
    html = f"<!doctype html><html><head><meta charset='utf-8'><style>{CSS}</style></head><body>{body}</body></html>"
    out = OUT
    if theme == "light":
        html = to_light(html)
    else:
        out = OUT_DARK
    PRES.mkdir(parents=True, exist_ok=True)
    HTML(string=html, base_url=str(PRES)).write_pdf(str(out))
    print(f"written {out}")
    return out


def main():
    ap = argparse.ArgumentParser(description="Build the CineMax progress presentation PDF.")
    ap.add_argument("--theme", choices=["light", "dark"], default="light",
                    help="light glass theme (default) or the original dark theme")
    args = ap.parse_args()
    build(args.theme)


if __name__ == "__main__":
    main()
