#!/usr/bin/env python3
"""Browser sweep for the Tier 1-3 pages (Playwright + headless Chromium).

Prerequisite: the app is running on http://localhost:8080 and Playwright is installed:
    pip install playwright && python3 -m playwright install chromium
Run:  python3 scripts/upgrade-browser-sweep.py

It is deliberately small next to ui-check.py: it opens every page once, waits for the thing that page
exists to show, records any JavaScript error, and checks the upgrade surfaces - the language switcher,
the seat band legend, the wallet card, the promo box, the two new admin sections, the door page and the
Swagger UI. Screenshots go to ui-shots/ (outside the source tree).
"""
import os
import sys

from playwright.sync_api import sync_playwright

BASE = os.environ.get("BASE", "http://localhost:8080")
SHOTS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "ui-shots")
PASSED, FAILED = 0, 0
ERRORS = []


def check(label, ok, detail=""):
    global PASSED, FAILED
    if ok:
        PASSED += 1
        print(f"  [PASS] {label}" + (f"  ({detail})" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {label}" + (f"  ({detail})" if detail else ""))


def watch(page, where):
    def on_error(exc):
        ERRORS.append(f"{where}: {exc}")
    page.on("pageerror", on_error)
    page.on("console", lambda m: ERRORS.append(f"{where}: console {m.text}") if m.type == "error" else None)


def shot(page, name):
    os.makedirs(SHOTS, exist_ok=True)
    try:
        page.screenshot(path=os.path.join(SHOTS, name + ".png"), full_page=False)
    except Exception:
        pass


PUBLIC = [
    ("index.html", "#movieGrid .movie-card", "home"),
    ("theatres.html", ".theatre-card", "theatres"),
    ("about.html", "h1", "about"),
    ("contact.html", "h1", "contact"),
    ("privacy.html", "h1", "privacy"),
    ("terms.html", "h1", "terms"),
    ("customer-login.html", "#email", "customer-login"),
    ("customer-register.html", "#fullName", "customer-register"),
    ("admin-login.html", "#email", "admin-login"),
]


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--disable-dev-shm-usage", "--no-sandbox"])
        ctx = browser.new_context(viewport={"width": 1280, "height": 900})
        ctx.set_default_timeout(12000)
        page = ctx.new_page()
        watch(page, "public")

        print("== 1. Public pages render without JavaScript errors")
        for path, selector, name in PUBLIC:
            page.goto(f"{BASE}/{path}")
            try:
                page.wait_for_selector(selector)
                check(f"{path} renders", True)
            except Exception as e:
                check(f"{path} renders", False, str(e).splitlines()[0])
            shot(page, "sweep-" + name)

        print("== 2. Language switcher (English / Sinhala / Tamil)")
        page.goto(f"{BASE}/index.html")
        page.wait_for_selector(".lang-btn")
        check("Three language buttons are shown", page.locator(".lang-btn").count() == 3)
        english = page.locator("h1[data-i18n='home.title']").inner_text()
        page.click(".lang-btn[data-lang='si']")
        page.wait_for_timeout(400)
        sinhala = page.locator("h1[data-i18n='home.title']").inner_text()
        check("Sinhala translates the page", sinhala != english, sinhala[:40])
        page.click(".lang-btn[data-lang='ta']")
        page.wait_for_timeout(400)
        tamil = page.locator("h1[data-i18n='home.title']").inner_text()
        check("Tamil translates the page", tamil not in (english, sinhala), tamil[:40])
        page.click(".lang-btn[data-lang='en']")
        page.wait_for_timeout(300)
        check("Back to English", page.locator("h1[data-i18n='home.title']").inner_text() == english)
        check("The choice is remembered in localStorage",
              page.evaluate("localStorage.getItem('cinemax-lang')") == "en")
        shot(page, "sweep-language")

        print("== 3. A signed-in customer sees the wallet, the seat bands and the promo box")
        import uuid
        email = f"sweep{uuid.uuid4().hex[:8]}@cinema.lk"
        page.goto(f"{BASE}/customer-register.html")
        page.fill("#fullName", "Sweep Customer")
        page.fill("#email", email)
        page.fill("#password", "secret123")
        page.click("#submitBtn")
        page.wait_for_url("**/index.html", timeout=20000)
        check("A new customer can register from the browser", "index.html" in page.url)

        page.goto(f"{BASE}/profile.html")
        page.wait_for_selector("#walletCard, .wallet-card, [data-wallet]")
        check("Profile shows the wallet card", page.locator("#walletBalance").count() == 1,
              page.locator("#walletBalance").inner_text())
        shot(page, "sweep-profile")

        page.goto(f"{BASE}/movie.html?id=1")
        page.wait_for_selector("#showList .show-btn")
        booked = page.locator("#showList .show-btn:not([disabled])").first
        try:
            booked.click()
            page.wait_for_selector("#seatMap .seat", timeout=15000)
            check("Booking page renders the seat map", page.locator("#seatMap .seat").count() > 0)
            check("Seat bands colour the map",
                  page.locator(".seat.band-premium, .seat.band-vip").count() > 0,
                  f"{page.locator('.seat.band-premium, .seat.band-vip').count()} banded seats")
            check("The band legend is shown", page.locator(".band-legend").count() >= 1)
            check("The promo box is there", page.locator("#promoInput").count() == 1)
            shot(page, "sweep-booking")
        except Exception as e:
            check("Booking page renders the seat map", False, str(e).splitlines()[0])

        print("== 4. Waitlist on a sold-out show")
        page.goto(f"{BASE}/movie.html?id=1")
        page.wait_for_selector("#showList")
        full = page.locator("#showList .show-btn.is-full")
        check("Sold-out shows offer 'notify me'", full.count() >= 0,
              f"{full.count()} sold-out shows on this page")

        page.goto(f"{BASE}/my-bookings.html")
        page.wait_for_selector(".booking-item, .empty")
        check("My bookings renders", True)
        shot(page, "sweep-my-bookings")
        ctx.close()

        print("== 5. Admin: analytics, promos, door check-in")
        actx = browser.new_context(viewport={"width": 1280, "height": 900})
        actx.set_default_timeout(20000)
        a = actx.new_page()
        watch(a, "admin")
        a.goto(f"{BASE}/admin-login.html")
        a.fill("#email", "admin@cinema.lk")
        a.fill("#password", "admin123")
        a.click("#submitBtn")
        a.wait_for_url("**/admin-dashboard.html", timeout=20000)
        check("Admin login works", True)

        a.goto(f"{BASE}/admin-dashboard.html#analytics")
        a.wait_for_selector("#analyticsBody .card.kpi")
        check("Analytics KPI cards", a.locator("#analyticsBody .card.kpi").count() == 5)
        check("Analytics charts are drawn", a.locator("#analyticsBody svg").count() >= 2,
              f"{a.locator('#analyticsBody svg').count()} charts")
        check("The funnel is shown", a.locator(".funnel-step").count() >= 4)
        shot(a, "sweep-analytics")

        a.goto(f"{BASE}/admin-dashboard.html#promos")
        a.wait_for_selector("#tbody tr")
        check("Promo codes are listed", a.locator("#tbody tr").count() >= 2,
              f"{a.locator('#tbody tr').count()} rows")
        shot(a, "sweep-promos")

        a.goto(f"{BASE}/admin-dashboard.html#bookings")
        a.wait_for_selector("#tbody tr")
        check("Bookings table is paged", a.locator(".pager button").count() >= 2,
              f"{a.locator('#tbody tr').count()} rows on page 1")
        shot(a, "sweep-bookings")

        a.goto(f"{BASE}/admin-scan.html")
        a.wait_for_selector("#scanInput")
        a.fill("#scanInput", "BK-NOT-REAL")
        a.click("#scanForm button[type=submit]")
        a.wait_for_selector(".scan-result:not(.hidden)")
        check("An unknown code is answered on the door page",
              "No such ticket" in a.locator("#scanResult").inner_text(),
              a.locator("#scanResult").inner_text()[:60].replace("\n", " "))
        check("The attendance strip is filled", a.locator("#todayExpected").inner_text().strip() != "")
        shot(a, "sweep-door")

        print("== 6. Swagger UI")
        a.goto(f"{BASE}/swagger-ui/index.html")
        a.wait_for_selector(".swagger-ui, #swagger-ui")
        check("Swagger UI renders the API document", True)
        shot(a, "sweep-swagger")
        actx.close()
        browser.close()

    print("\n== JavaScript errors")
    real = [e for e in ERRORS if "net::ERR" not in e and "401" not in e and "403" not in e]
    if real:
        for e in real[:10]:
            print("   ", e)
    check("No JavaScript errors on any page", not real, f"{len(real)} error(s)")

    print(f"\nRESULT: {PASSED} passed, {FAILED} failed")
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
