#!/usr/bin/env python3
"""Browser check for the CineMax front end (Playwright + headless Chromium).

Prerequisite: the app is running on http://localhost:8080 and Playwright is installed:
    pip install playwright && python3 -m playwright install chromium
Run:  python3 scripts/ui-check.py

It walks the real pages the way a user would:
  * public pages render (home, movie, theatres)
  * a NEW customer registers, reserves seats, pays by card and sees the booking in "My bookings"
  * guarded pages redirect anonymous visitors to the login page
  * the admin logs in on its own portal and every dashboard section loads
  * the admin creates and deletes a test movie through the generic CRUD dialog
Screenshots go to ui-shots/ (outside the source tree). JavaScript errors are collected and reported.
"""
import os
import sys
import time

from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get("CINEMA_BASE", "http://localhost:8080")
SHOTS = os.environ.get("CINEMA_SHOTS", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ui-shots"))
os.makedirs(SHOTS, exist_ok=True)

results = []
js_errors = []


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (f"  ({detail})" if detail else ""))


def watch(page, label):
    def on_console(msg):
        text = msg.text
        # 401 responses from /api/auth/me on logged-out pages are expected noise
        if msg.type == "error" and "status of 401" not in text and "Failed to load resource" not in text:
            js_errors.append(f"{label}: {text}")

    page.on("pageerror", lambda exc: js_errors.append(f"{label}: pageerror {exc}"))
    page.on("console", on_console)


def shot(page, name):
    page.screenshot(path=os.path.join(SHOTS, name + ".png"), full_page=True)


def main():
    stamp = str(int(time.time()))
    fresh_email = f"ui{stamp}@test.lk"

    with sync_playwright() as p:
        browser = p.chromium.launch()
        viewport = {"width": 1280, "height": 900}

        # ---------- 1. public pages ----------
        print("== 1. Public pages")
        ctx = browser.new_context(viewport=viewport)
        page = ctx.new_page()
        watch(page, "public")
        page.goto(f"{BASE}/index.html")
        page.wait_for_selector("#movieGrid .movie-card")
        check("Home lists movies", page.locator("#movieGrid .movie-card").count() >= 1,
              f"{page.locator('#movieGrid .movie-card').count()} cards")
        shot(page, "01-home")

        # The language switcher (Tier 2) swaps the words, not the layout.
        page.wait_for_selector(".lang-btn")
        english_nav = page.locator(".nav-links a[data-nav='movies']").inner_text()
        english_title = page.locator("h1[data-i18n='home.title']").inner_text()
        page.click(".lang-btn[data-lang='si']")
        page.wait_for_timeout(500)
        sinhala_nav = page.locator(".nav-links a[data-nav='movies']").inner_text()
        check("Switching to Sinhala translates the hero heading",
              page.locator("h1[data-i18n='home.title']").inner_text() != english_title,
              page.locator("h1[data-i18n='home.title']").inner_text())
        check("Switching to Sinhala re-renders the navigation", sinhala_nav != english_nav,
              f"{english_nav} -> {sinhala_nav}")
        page.click(".lang-btn[data-lang='en']")
        page.wait_for_timeout(400)
        check("Switching back to English restores the labels",
              page.locator("h1[data-i18n='home.title']").inner_text() == english_title)
        shot(page, "01b-home-sinhala")

        page.goto(f"{BASE}/movie.html?id=1")
        page.wait_for_selector("#showList .show-btn")
        check("Movie page shows showtimes", page.locator("#showList .show-btn").count() >= 1)
        shot(page, "02-movie")



        page.goto(f"{BASE}/theatres.html")
        page.wait_for_selector(".theatre-card")
        check("Theatres page lists theatres with screens", page.locator(".theatre-card").count() >= 2)
        shot(page, "03-theatres")

        # ---------- 2. customer journey (new account) ----------
        print("== 2. Customer: register -> book -> pay -> my bookings")
        page.goto(f"{BASE}/customer-register.html")
        page.fill("#fullName", "UI Check Customer")
        page.fill("#email", fresh_email)
        page.fill("#password", "secret123")
        page.click("#submitBtn")
        page.wait_for_url("**/index.html", timeout=15000)
        check("Registration logs the new customer in and redirects home", "index.html" in page.url)

        page.goto(f"{BASE}/movie.html?id=1")
        page.wait_for_selector("#showList .show-btn")
        page.locator("#showList .show-btn").first.click()
        page.wait_for_selector("#seatMap .seat")
        check("Booking page renders seat map", page.locator("#seatMap .seat").count() > 0,
              f"{page.locator('#seatMap .seat').count()} seats")
        free = page.locator("#seatMap .seat:not([disabled])")
        free.nth(0).click()
        free.nth(1).click()
        expect(page.locator("#sSeats")).not_to_have_text("None selected")
        check("Selecting seats updates the summary", True, page.locator("#sSeats").inner_text())
        shot(page, "04-seat-selection")

        page.click("#mainBtn")
        page.wait_for_url("**/booking.html?bookingId=*", timeout=15000)
        check("Reserving seats creates a pending booking", "bookingId=" in page.url)
        page.wait_for_selector("#paymentCard:not(.hidden)")
        page.select_option("#method", "CARD")
        page.fill("#reference", "4111111111111111")
        page.click("#payBtn")
        page.wait_for_selector("#receipt:not(.hidden)")
        expect(page.locator("#receipt")).to_contain_text("Booking confirmed", timeout=15000)
        check("Card payment confirms the booking and shows receipt", True)
        shot(page, "05-payment-receipt")

        page.goto(f"{BASE}/my-bookings.html")
        page.wait_for_selector(".booking-item")
        check("My bookings lists the confirmed booking",
              page.locator(".booking-item").filter(has_text="Confirmed").count() >= 1)
        shot(page, "06-my-bookings")

        page.goto(f"{BASE}/profile.html")
        expect(page.locator("#fullName")).to_have_value("UI Check Customer")
        check("Profile loads and shows loyalty points", page.locator("#points").inner_text().strip() != "")
        shot(page, "07-profile")
        ctx.close()

        # ---------- 3. guards ----------
        print("== 3. Route guards")
        anon = browser.new_context(viewport=viewport)
        g = anon.new_page()
        watch(g, "guard")
        g.goto(f"{BASE}/my-bookings.html")
        g.wait_for_url("**/customer-login.html**", timeout=15000)
        check("Anonymous visitor to My bookings is sent to customer login", "customer-login.html" in g.url)
        g.goto(f"{BASE}/admin-dashboard.html")
        g.wait_for_url("**/admin-login.html**", timeout=15000)
        check("Anonymous visitor to admin dashboard is sent to admin login", "admin-login.html" in g.url)
        anon.close()

        # ---------- 4. admin portal ----------
        print("== 4. Admin: login -> every section -> CRUD")
        actx = browser.new_context(viewport=viewport)
        a = actx.new_page()
        watch(a, "admin")
        a.goto(f"{BASE}/admin-login.html")
        a.fill("#email", "admin@cinema.lk")
        a.fill("#password", "admin123")
        a.click("#submitBtn")
        a.wait_for_url("**/admin-dashboard.html", timeout=15000)
        check("Admin login redirects to dashboard", True)

        a.wait_for_selector(".card.kpi")
        check("Admin overview shows summary cards", a.locator(".card.kpi").count() == 5)
        shot(a, "08-admin-overview")

        sections = ["analytics", "customers", "promos", "admins", "movies", "theatres", "screens", "shows",
                    "bookings", "payments", "files"]
        for key in sections:
            a.goto(f"{BASE}/admin-dashboard.html#{key}")
            if key == "files":
                a.wait_for_selector("[data-run]")
                check("Files section shows import and export actions", a.locator("[data-run]").count() == 2)
            elif key == "analytics":
                a.wait_for_selector("#analyticsBody .card.kpi", timeout=20000)
                check("Analytics section draws the KPI cards and charts",
                      a.locator("#analyticsBody .card.kpi").count() == 5
                      and a.locator("#analyticsBody svg").count() >= 2,
                      f"{a.locator('#analyticsBody svg').count()} charts")
            elif key == "bookings":
                a.wait_for_selector("#tbody tr", timeout=20000)
                check("Paged bookings table shows a pager", a.locator(".pager button").count() >= 2,
                      f"{a.locator('#tbody tr').count()} rows")
            elif key == "promos":
                a.wait_for_selector("#tbody tr", timeout=20000)
                check("Promo codes section lists the seeded codes",
                      a.locator("#tbody tr").count() >= 2, f"{a.locator('#tbody tr').count()} rows")
            else:
                a.wait_for_selector("#tbody tr, #sectionBody .card, .empty", timeout=15000)
                rows = a.locator("#tbody tr").count()
                check(f"Section '{key}' loads", rows >= 1 or key in ("bookings", "payments", "customers"),
                      f"{rows} rows")
            shot(a, "09-admin-" + key)

        # Create + delete a movie via the generic CRUD dialog
        a.goto(f"{BASE}/admin-dashboard.html#movies")
        a.wait_for_selector("#newBtn")
        a.click("#newBtn")
        a.wait_for_selector("#modal.open")
        title = f"UI Test Movie {stamp}"
        a.fill("input[name=title]", title)
        a.fill("input[name=genre]", "Test")
        a.fill("input[name=language]", "English")
        a.fill("input[name=durationMinutes]", "95")
        a.select_option("select[name=format]", "IMAX")
        a.click("#formSubmit")
        a.wait_for_selector(f"#tbody tr:has-text('{title}')", timeout=15000)
        check("Admin creates a movie through the dialog", True, title)
        row = a.locator("#tbody tr", has_text=title)
        check("New movie shows IMAX badge with +LKR 300 surcharge",
              "300" in row.inner_text() and "IMAX" in row.inner_text())

        a.once("dialog", lambda d: d.accept())
        row.locator("[data-del]").click()
        expect(a.locator("#tbody")).not_to_contain_text(title, timeout=15000)
        check("Admin deletes the test movie", True)

        # Validation: missing required field shows an inline error
        a.click("#newBtn")
        a.wait_for_selector("#modal.open")
        a.fill("input[name=title]", "")
        a.click("#formSubmit")
        check("Required-field validation blocks empty submission",
              a.locator("#modal.open").count() == 1)
        a.click("#formCancel")
        # ---------- 5. door check-in ----------
        print("== 5. Door check-in page")
        a.goto(f"{BASE}/admin-scan.html")
        a.wait_for_selector("#scanInput")
        a.fill("#scanInput", "BK-NOT-REAL")
        a.click("#scanForm button[type=submit]")
        a.wait_for_selector(".scan-result:not(.hidden)")
        check("An unknown ticket is answered on the page",
              "No such ticket" in a.locator("#scanResult").inner_text(),
              a.locator("#scanResult").inner_text()[:80])
        check("The attendance strip is filled in", a.locator("#todayExpected").inner_text().strip() != "",
              a.locator("#todayExpected").inner_text())
        shot(a, "10-door-checkin")
        actx.close()

        browser.close()

    print()
    failed = [r for r in results if not r[1]]
    if js_errors:
        print("JavaScript errors captured:")
        for e in js_errors:
            print("   -", e)
    else:
        print("No JavaScript errors captured.")
    print(f"RESULT: {len(results) - len(failed)} passed, {len(failed)} failed")
    return 1 if failed or js_errors else 0


if __name__ == "__main__":
    sys.exit(main())
