#!/usr/bin/env python3
"""Accessibility pass with axe-core (the engine Lighthouse uses for its a11y category).

Prerequisites: the app is running on http://localhost:8080, and
    pip install playwright && python3 -m playwright install chromium
    npm install axe-core        (in this folder or anywhere; AXE_PATH can point at axe.min.js)
Run:  python3 scripts/a11y-check.py

It injects axe-core into each page, runs the WCAG 2.1 A/AA rule set and prints every violation with the
element that breaks it. Exit code 0 means no violation of impact "serious" or "critical".
"""
import json
import os
import sys

from playwright.sync_api import sync_playwright

BASE = os.environ.get("BASE", "http://localhost:8080")
AXE = os.environ.get("AXE_PATH", os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                              "..", "node_modules", "axe-core", "axe.min.js"))

PAGES = [
    ("index.html", "Home"),
    ("theatres.html", "Theatres"),
    ("movie.html?id=1", "Film page"),
    ("about.html", "About"),
    ("contact.html", "Contact"),
    ("privacy.html", "Privacy"),
    ("terms.html", "Terms"),
    ("customer-login.html", "Customer login"),
    ("customer-register.html", "Customer register"),
    ("admin-login.html", "Admin login"),
]

ADMIN_PAGES = [
    ("admin-dashboard.html#analytics", "Admin analytics"),
    ("admin-dashboard.html#promos", "Admin promo codes"),
    ("admin-dashboard.html#bookings", "Admin bookings"),
    ("admin-scan.html", "Door check-in"),
]


def run_axe(page):
    return page.evaluate("""async () => {
        const r = await axe.run(document, {
            runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] }
        });
        return r.violations.map(v => ({
            id: v.id, impact: v.impact, help: v.help,
            nodes: v.nodes.slice(0, 3).map(n => n.target.join(' '))
        }));
    }""")


def main():
    if not os.path.exists(AXE):
        print(f"axe-core not found at {AXE}\n  npm install axe-core    (or set AXE_PATH)")
        return 2
    axe_source = open(AXE, encoding="utf-8").read()

    serious, total, checked = 0, 0, 0
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--disable-dev-shm-usage", "--no-sandbox"])
        ctx = browser.new_context(viewport={"width": 1280, "height": 900})
        ctx.set_default_timeout(15000)
        page = ctx.new_page()

        def audit(path, label):
            nonlocal serious, total, checked
            page.goto(f"{BASE}/{path}")
            page.wait_for_timeout(700)
            page.add_script_tag(content=axe_source)
            violations = run_axe(page)
            checked += 1
            hard = [v for v in violations if v["impact"] in ("serious", "critical")]
            total += len(violations)
            serious += len(hard)
            if violations:
                print(f"  {label}: {len(violations)} rule(s)")
                for v in violations:
                    mark = "!!" if v["impact"] in ("serious", "critical") else "  "
                    print(f"    {mark} [{v['impact']}] {v['id']} - {v['help']}")
                    for node in v["nodes"]:
                        print(f"         {node}")
            else:
                print(f"  {label}: clean")

        print("== Public pages")
        for path, label in PAGES:
            audit(path, label)

        # Signed in as a customer, for the pages that need a session.
        print("== Customer pages")
        import uuid
        email = f"a11y{uuid.uuid4().hex[:8]}@cinema.lk"
        page.goto(f"{BASE}/customer-register.html")
        page.fill("#fullName", "A11y Check")
        page.fill("#email", email)
        page.fill("#password", "secret123")
        page.click("#submitBtn")
        page.wait_for_url("**/index.html", timeout=20000)
        for path, label in [("profile.html", "Profile / wallet"),
                            ("my-bookings.html", "My bookings")]:
            audit(path, label)

        # And as the admin, for the dashboard pages.
        print("== Admin pages")
        page.goto(f"{BASE}/admin-login.html")
        page.fill("#email", "admin@cinema.lk")
        page.fill("#password", "admin123")
        page.click("#submitBtn")
        page.wait_for_url("**/admin-dashboard.html", timeout=20000)
        for path, label in ADMIN_PAGES:
            audit(path, label)

        browser.close()

    print(f"\nRESULT: {checked} pages checked, {total} rule violation(s), {serious} of them serious or critical")
    return 0 if serious == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
