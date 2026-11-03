#!/usr/bin/env python3
"""API checks for the Tier 1-3 upgrade, one HTTP call per feature.

Run the app first (mvn spring-boot:run), then:  python3 scripts/upgrade-api-check.py

Unlike smoke-test.py (which walks every CRUD of the coursework), this script proves the upgrade itself:
promo codes, the wallet ledger, seat bands, part-cancellation, the waitlist, door check-in, the role
fence, analytics, paging, the language files and the OpenAPI document.
"""
import datetime
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8080"
PASSED, FAILED = 0, 0


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with self.opener.open(req) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw


def check(label, ok, detail=""):
    global PASSED, FAILED
    if ok:
        PASSED += 1
        print(f"  [PASS] {label}" + (f"  ({detail})" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {label}" + (f"  ({detail})" if detail else ""))


def login(path, email, password):
    c = Client()
    status, _ = c.call("POST", path, {"email": email, "password": password})
    return c if status == 200 else None


def new_customer():
    c = Client()
    email = f"tier{uuid.uuid4().hex[:8]}@cinema.lk"
    c.call("POST", "/api/auth/customer/register",
           {"fullName": "Tier Three", "email": email, "password": "secret123"})
    login("/api/auth/customer/login", email, "secret123")
    # A brand-new Customer object keeps the same cookie jar, so log in again on a fresh client.
    return login("/api/auth/customer/login", email, "secret123")


def main():
    admin = login("/api/auth/admin/login", "admin@cinema.lk", "admin123")
    if not admin:
        print("Admin login failed - is the app running on localhost:8080?")
        return 1
    customer = new_customer()

    print("== 1. Seat bands (Tier 2)")
    _, shows = customer.call("GET", "/api/shows")
    # A show at least two days away, so the refund tiers below are the friendly (100 %) ones.
    cutoff = (datetime.datetime.now() + datetime.timedelta(days=2)).isoformat()
    far = [s for s in shows if s["startTime"] > cutoff and s["availableSeats"] > 20]
    future = (far or shows)[0]
    show_id = future["id"]
    _, show = customer.call("GET", f"/api/shows/{show_id}")
    bands = show.get("seatBands") or []
    check("A show carries its band legend", len(bands) == 3, f"{len(bands)} bands")
    prices = {b["band"]: float(b["price"]) for b in bands}
    check("Regular < Premium < VIP", prices.get("REGULAR", 0) < prices.get("PREMIUM", 1e9)
          < prices.get("VIP", 2e9), f"{prices}")
    check("Every band says which rows it covers", all(b["rows"] for b in bands))
    # Two seats that are still free on that show, whatever earlier runs left behind.
    all_seats = [f"{chr(ord('A') + row)}{seat}"
                 for row in range(show["rowCount"]) for seat in range(1, show["seatsPerRow"] + 1)]
    free = [code for code in all_seats if code not in set(show["bookedSeats"])]
    seat_a, seat_b = free[0], free[1]

    print("== 2. Promo codes (Tier 1)")
    code = "TIER" + uuid.uuid4().hex[:5].upper()
    status, promo = admin.call("POST", "/api/admin/promos",
                               {"code": code, "type": "FLAT", "value": 400, "minSpend": 2000,
                                "validFrom": "2026-01-01", "validUntil": "2027-12-31",
                                "maxUses": 50, "description": "tier check"})
    check("Admin creates a flat-amount promo", status in (200, 201), f"{status}")
    promo_id = promo.get("id") if isinstance(promo, dict) else None
    status, quote = customer.call("POST", "/api/customer/promo/check", {"code": code, "total": 3000})
    check("A flat promo takes its amount off", status == 200 and float(quote["discount"]) == 400.0, f"{quote}")
    check("The new total is returned", float(quote["newTotal"]) == 2600.0, f"{quote['newTotal']}")
    status, quote = customer.call("POST", "/api/customer/promo/check", {"code": code, "total": 1500})
    check("Under the minimum spend the code does not apply", quote["valid"] is False)
    status, body = admin.call("POST", "/api/admin/promos",
                              {"code": code, "type": "FLAT", "value": 1, "minSpend": 0,
                               "validFrom": "2026-01-01", "validUntil": "2027-12-31"})
    check("A duplicate code is refused", status == 400, f"{status}")

    print("== 3. Booking with a promo (Tier 1 + Tier 2)")
    status, booking = customer.call("POST", "/api/customer/bookings",
                                    {"showId": show_id, "seatCodes": [seat_a, seat_b], "promoCode": code})
    check("A booking can carry a promo code", status == 201 and booking.get("promoCode") == code,
          f"{status} {booking.get('promoCode')}")
    check("The discount is stored on the booking", float(booking.get("discountAmount") or 0) > 0,
          f"{booking.get('discountAmount')}")
    booking_id = booking["id"]

    print("== 4. Wallet ledger (Tier 2)")
    status, wallet = customer.call("GET", "/api/customer/wallet")
    check("A new customer has an empty wallet", status == 200 and float(wallet["balance"]) == 0.0)
    status, wallet = customer.call("POST", "/api/customer/wallet/top-up", {"amount": 8000, "method": "CARD"})
    check("Top-up raises the balance", status == 200 and float(wallet["balance"]) >= 8000, f"{wallet['balance']}")
    check("The top-up is a ledger row", wallet["entries"][0]["type"] == "TOP_UP")
    status, body = customer.call("POST", "/api/customer/wallet/top-up", {"amount": 10, "method": "CARD"})
    check("A top-up under LKR 50 is refused", status == 400)

    print("== 5. Paying from the wallet and a part refund (Tier 2)")
    status, payment = customer.call("POST", "/api/customer/payments",
                                    {"bookingId": booking_id, "method": "WALLET", "reference": "WALLET"})
    check("Wallet payment succeeds", status == 201 and payment["status"] == "SUCCESS", f"{status}")
    _, wallet = customer.call("GET", "/api/customer/wallet")
    check("The payment is a PAYMENT row", wallet["entries"][0]["type"] == "PAYMENT",
          f"balance {wallet['balance']}")
    status, note = customer.call("POST", f"/api/customer/bookings/{booking_id}/cancel-seats",
                                 {"seatCodes": [seat_b]})
    check("One seat of two can be given back", status == 200 and float(note["refundAmount"]) > 0, f"{note}")
    check("The refund follows the 24h policy", "Full refund" in note["policy"], note["policy"])
    _, wallet = customer.call("GET", "/api/customer/wallet")
    check("The refund is a REFUND row", wallet["entries"][0]["type"] == "REFUND")
    _, kept = customer.call("GET", f"/api/customer/bookings/{booking_id}")
    check("The booking keeps the other seat", kept["seatCodes"] == [seat_a] and kept["status"] == "CONFIRMED",
          f"{kept['seatCodes']} {kept['status']}")
    status, body = customer.call("POST", f"/api/customer/bookings/{booking_id}/cancel-seats",
                                 {"seatCodes": [seat_a]})
    check("The last seat needs a full cancellation", status == 400, f"{status}")

    print("== 6. Door check-in (Tier 1)")
    status, body = admin.call("POST", "/api/admin/tickets/validate", {"code": "BK00000000"})
    check("An unknown code gives NOT_FOUND", body["result"] == "NOT_FOUND")
    status, today = admin.call("GET", "/api/admin/tickets/today")
    check("Today's attendance is summarised", status == 200
          and {"expected", "checkedIn", "noShows", "attendancePercent"} <= set(today), f"{list(today)[:5]}")
    status, body = admin.call("POST", "/api/admin/tickets/validate", {})
    check("An empty scan is answered, not crashed", status in (200, 400), f"{status}")

    print("== 7. The BOX_OFFICE role fence (Tier 2)")
    door = login("/api/auth/admin/login", "door@cinema.lk", "door1234")
    check("The door account exists and logs in", door is not None)
    if door:
        status, body = door.call("POST", "/api/admin/tickets/validate", {"code": "BK00000000"})
        check("It may scan tickets", status == 200 and body["result"] == "NOT_FOUND", f"{status}")
        status, body = door.call("GET", "/api/admin/customers")
        check("It may not read customers", status == 403, f"{status}")
        status, body = door.call("GET", "/api/admin/analytics?days=7")
        check("It may not read the analytics", status == 403 and "box-office" in body["message"], f"{status}")
        status, body = door.call("GET", "/api/admin/bookings/page?page=0&size=5")
        check("It may look bookings up", status == 200, f"{status}")

    print("== 8. Analytics and paging (Tier 1)")
    status, an = admin.call("GET", "/api/admin/analytics?days=30")
    check("Analytics answers", status == 200 and an["days"] == 30, f"window {an.get('days')} days")
    check("Revenue per screen is not empty", len(an["revenueByScreen"]) >= 1,
          f"{len(an['revenueByScreen'])} screens")
    check("Revenue per film is not empty", len(an["revenueByMovie"]) >= 1)
    check("The funnel adds up", an["funnel"]["seatHolds"] >= an["funnel"]["paidBookings"], f"{an['funnel']}")
    check("Occupancy is a percentage", 0 <= an["occupancyPercent"] <= 100, f"{an['occupancyPercent']}")
    status, page = admin.call("GET", "/api/admin/bookings/page?page=1&size=20")
    check("Bookings page 2 has rows", status == 200 and page["number"] == 1
          and len(page["content"]) <= 20, f"{len(page.get('content', []))} rows")
    status, page = admin.call("GET", "/api/admin/customers/page?page=0&size=5")
    check("Customers are paged", status == 200 and page["size"] == 5)

    print("== 9. Waitlist for a sold-out show (Tier 1)")
    # Fill the smallest *upcoming* screen completely, ten seats at a time (the per-booking limit).
    status, shows = admin.call("GET", "/api/admin/shows")
    now = datetime.datetime.now().isoformat()
    upcoming = [s for s in shows if s["startTime"] > now]
    smallest = min(upcoming, key=lambda s: s["rowCount"] * s["seatsPerRow"])
    fill_show = smallest["id"]
    seats = [f"{chr(ord('A') + row)}{seat}"
             for row in range(smallest["rowCount"]) for seat in range(1, smallest["seatsPerRow"] + 1)]
    filler = new_customer()
    for i in range(0, len(seats), 10):
        chunk = seats[i:i + 10]
        status, _ = filler.call("POST", "/api/customer/bookings", {"showId": fill_show, "seatCodes": chunk})
        if status != 201:
            for seat in chunk:   # a single seat may already be gone; take the rest one by one
                filler.call("POST", "/api/customer/bookings", {"showId": fill_show, "seatCodes": [seat]})
    status, filled = customer.call("GET", f"/api/shows/{fill_show}")
    check("A show can be filled seat by seat", filled["availableSeats"] == 0,
          f"{filled['availableSeats']} seats left of {filled['totalSeats']}")
    status, body = customer.call("POST", f"/api/customer/waitlist/{fill_show}", {})
    check("A sold-out show takes a waitlist entry", status in (200, 201), f"{status} {body}")
    status, mine = customer.call("GET", "/api/customer/waitlist")
    check("The entry is listed for the customer", status == 200 and len(mine) >= 1, f"{len(mine)} entries")
    status, body = customer.call("DELETE", f"/api/customer/waitlist/{fill_show}")
    check("The customer can leave the waitlist", status == 200, f"{status}")

    print("== 10. Language files and the API document (Tier 2 + 3)")
    for lang in ("en", "si", "ta"):
        with urllib.request.urlopen(BASE + f"/lang/{lang}.json") as r:
            table = json.loads(r.read().decode())
        check(f"lang/{lang}.json is served", r.status == 200 and len(table) >= 20, f"{len(table)} keys")
    status, docs = admin.call("GET", "/v3/api-docs")
    paths = docs.get("paths", {}) if isinstance(docs, dict) else {}
    check("The OpenAPI document lists the upgrade endpoints",
          status == 200 and "/api/admin/analytics" in paths and "/api/customer/wallet" in paths
          and "/api/admin/tickets/validate" in paths, f"{len(paths)} paths")
    check("The document has a security scheme", "securitySchemes" in docs.get("components", {}))

    print("== 11. Cleanup")
    customer.call("DELETE", f"/api/customer/bookings/{booking_id}")   # cancel, then remove the row
    status, _ = admin.call("DELETE", f"/api/admin/bookings/{booking_id}")
    check("The test booking is cancelled and removed", status == 200, f"{status}")
    # Release every seat this run held, so the demo data stays tidy.
    for client in (customer, filler):
        status, mine = client.call("GET", "/api/customer/bookings")
        for b in mine or []:
            if b["status"] == "PENDING_PAYMENT":
                client.call("DELETE", f"/api/customer/bookings/{b['id']}")
        client.call("DELETE", "/api/customer/profile")
    if promo_id:
        status, _ = admin.call("DELETE", f"/api/admin/promos/{promo_id}")
        check("The test promo code is deleted", status == 200, f"{status}")

    print(f"\nRESULT: {PASSED} passed, {FAILED} failed")
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
