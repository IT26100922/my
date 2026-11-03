"""End-to-end smoke test for the Movie Ticket Booking API.
Run the app first (mvn spring-boot:run), then:  python3 scripts/smoke-test.py
"""
import http.cookiejar, json, sys, urllib.request, urllib.error, uuid, datetime

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


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}")
    else:
        FAILED += 1
        print(f"  [FAIL] {name} {detail}")


admin, customer, customer2 = Client(), Client(), Client()

print("== 1. Public READ endpoints")
s, movies = admin.call("GET", "/api/movies")
check("GET /api/movies returns seeded movies", s == 200 and len(movies) >= 6, f"status={s}")
s, shows = admin.call("GET", "/api/shows")
check("GET /api/shows returns upcoming showtimes", s == 200 and len(shows) > 0, f"status={s}")
s, theatres = admin.call("GET", "/api/theatres")
check("GET /api/theatres returns theatres", s == 200 and len(theatres) >= 1)
s, screens = admin.call("GET", "/api/screens")
check("GET /api/screens returns screens", s == 200 and len(screens) >= 3)
s, _ = admin.call("GET", "/api/auth/me")
check("GET /api/auth/me is 401 when logged out", s == 401)

print("== 2. Separate login methods and role protection")
s, body = customer.call("POST", "/api/auth/customer/login", {"email": "admin@cinema.lk", "password": "admin123"})
check("Admin account cannot use customer login", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "customer@cinema.lk", "password": "customer123"})
check("Customer account cannot use admin login", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "admin@cinema.lk", "password": "wrong"})
check("Wrong admin password rejected", s == 400)
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "admin@cinema.lk", "password": "admin123"})
check("Admin login succeeds", s == 200 and body["role"] == "ADMIN", f"status={s} body={body}")
s, body = customer.call("POST", "/api/auth/customer/login", {"email": "customer@cinema.lk", "password": "customer123"})
check("Customer login succeeds", s == 200 and body["role"] == "CUSTOMER", f"status={s} body={body}")
s, _ = customer.call("GET", "/api/admin/customers")
check("Customer session blocked from admin API", s == 403, f"status={s}")
s, _ = Client().call("GET", "/api/customer/bookings")
check("Anonymous user blocked from customer API", s == 401, f"status={s}")

print("== 3. Member 2 - Movie CRUD (admin)")
title = "Test Movie " + uuid.uuid4().hex[:6]
movie_req = {"title": title, "genre": "Comedy", "language": "English", "durationMinutes": 100,
             "rating": "U", "description": "Test", "posterUrl": None, "releaseDate": "2026-10-01", "format": "IMAX"}
s, created = admin.call("POST", "/api/admin/movies", movie_req)
check("CREATE movie (IMAX subclass via factory)", s == 201 and created["format"] == "IMAX", f"status={s} {created}")
mid = created["id"] if s == 201 else None
s, got = admin.call("GET", f"/api/movies/{mid}")
check("READ movie by id", s == 200 and got["title"] == title)
movie_req["genre"] = "Action"
s, upd = admin.call("PUT", f"/api/admin/movies/{mid}", movie_req)
check("UPDATE movie genre", s == 200 and upd["genre"] == "Action", f"status={s}")
movie_req["format"] = "STANDARD"
s, body = admin.call("PUT", f"/api/admin/movies/{mid}", movie_req)
check("UPDATE rejects format change (subclass is fixed)", s == 400, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/movies/{mid}")
check("DELETE movie", s == 200, f"status={s} {body}")
s, body = customer.call("POST", "/api/admin/movies", movie_req)
check("Customer cannot create movies", s == 403)

print("== 4. Member 3 - Theatre & Screen CRUD")
s, th = admin.call("POST", "/api/admin/theatres", {"name": "Test Theatre", "city": "Kandy", "address": "x", "phone": None})
check("CREATE theatre", s == 201, f"status={s} {th}")
tid = th["id"] if s == 201 else None
s, sc = admin.call("POST", "/api/admin/screens", {"name": "VIP test", "theatreId": tid, "screenType": "VIP", "rowCount": 3, "seatsPerRow": 4})
check("CREATE VIP screen (subclass + multiplier)", s == 201 and sc["priceMultiplier"] in (1.8, 1.80), f"status={s} {sc}")
scid = sc["id"] if s == 201 else None
s, sc2 = admin.call("PUT", f"/api/admin/screens/{scid}", {"name": "VIP renamed", "theatreId": tid, "screenType": "VIP", "rowCount": 3, "seatsPerRow": 4})
check("UPDATE screen name", s == 200 and sc2["name"] == "VIP renamed", f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/theatres/{tid}")
check("DELETE theatre blocked while screens exist", s == 400, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/screens/{scid}")
check("DELETE screen", s == 200, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/theatres/{tid}")
check("DELETE theatre after screens removed", s == 200, f"status={s}")

print("== 5. Member 4 - Showtime CRUD and rules")
future = (datetime.datetime.now() + datetime.timedelta(days=5)).replace(hour=10, minute=0, second=0, microsecond=0)
mid2 = movies[0]["id"]
screen_id = screens[0]["id"]
show_req = {"movieId": mid2, "screenId": screen_id, "startTime": future.isoformat(), "basePrice": 1500}
s, show = admin.call("POST", "/api/admin/shows", show_req)
# An earlier interrupted run may still hold that slot, so walk forward a day at a time until one is free.
for _ in range(30):
    if s == 201:
        break
    future = future + datetime.timedelta(days=1)
    show_req = {**show_req, "startTime": future.isoformat()}
    s, show = admin.call("POST", "/api/admin/shows", show_req)
check("CREATE showtime", s == 201, f"status={s} {show}")
new_id = show["id"] if s == 201 else None
s, body = admin.call("POST", "/api/admin/shows", show_req)
check("Overlapping showtime on same screen rejected", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/admin/shows", {**show_req, "startTime": (datetime.datetime.now() - datetime.timedelta(days=1)).isoformat()})
check("Showtime in the past rejected", s == 400, f"status={s}")
if new_id:
    s, upd_show = admin.call("PUT", f"/api/admin/shows/{new_id}", {**show_req, "basePrice": 1600})
    check("UPDATE showtime base price", s == 200 and float(upd_show["basePrice"]) == 1600.0, f"status={s}")
    check("Pricing strategy chosen (matinee 10:00 -> 0.8x)", upd_show["pricingStrategy"] == "Matinee", f"got {upd_show.get('pricingStrategy')}")

print("== 6. Member 5 + 6 - Customer booking, payment and cancellation")
s, chosen = customer.call("GET", f"/api/shows/{new_id}")
check("GET showtime with seat map", s == 200 and chosen["totalSeats"] > 0)
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["A1", "A2"]})
check("CREATE booking (2 seats)", s == 201 and body["status"] == "PENDING_PAYMENT", f"status={s} {body}")
bid = body["id"] if s == 201 else None
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["A1"]})
check("Seat already held by another booking is rejected", s == 400 and "already taken" in body["message"], f"{s} {body}")
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["Z9"]})
check("Invalid seat code rejected", s == 400, f"status={s}")
s, body = customer2.call("POST", "/api/auth/customer/register", {"fullName": "Test Buyer", "email": f"t{uuid.uuid4().hex[:6]}@x.com", "password": "secret123", "phone": None})
check("REGISTER new customer (CREATE)", s == 201, f"status={s} {body}")
email2 = body.get("email") if s == 201 else None
customer2.call("POST", "/api/auth/customer/login", {"email": email2, "password": "secret123"})
s, body = customer2.call("GET", f"/api/customer/bookings/{bid}")
check("Another customer cannot read this booking", s == 404, f"status={s}")
s, body = customer.call("PUT", f"/api/customer/bookings/{bid}", {"seatCodes": ["B3", "B4", "B5"]})
check("UPDATE booking seats (3 seats)", s == 200 and len(body["seatCodes"]) == 3, f"status={s} {body}")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "CARD", "reference": "1234"})
check("Card payment with invalid number rejected", s == 400, f"status={s}")
s, before = customer.call("GET", "/api/customer/profile")
# Tier 2: a wallet payment now really moves money, so the wallet has to be filled first.
s, top = customer.call("POST", "/api/customer/wallet/top-up", {"amount": 5000, "method": "CARD"})
check("Wallet top-up writes a TOP_UP ledger row", s == 200 and float(top["balance"]) >= 5000
      and top["entries"][0]["type"] == "TOP_UP", f"status={s} {top}")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "WALLET", "reference": "077123456"})
check("Wallet payment (polymorphic processor) succeeds", s == 201 and body["status"] == "SUCCESS", f"status={s} {body}")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "WALLET", "reference": "077123456"})
check("Paying twice is rejected", s == 400)
s, b2 = customer.call("GET", f"/api/customer/bookings/{bid}")
check("Booking is CONFIRMED after payment", b2["status"] == "CONFIRMED", f"{b2}")
s, body = customer.call("PUT", f"/api/customer/bookings/{bid}", {"seatCodes": ["C1"]})
check("Confirmed booking cannot change seats", s == 400)
s, prof = customer.call("GET", "/api/customer/profile")
check("Loyalty points awarded (1 per seat)", s == 200 and prof["loyaltyPoints"] - before["loyaltyPoints"] == 3, f"{prof}")
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["D1"]})
bid2 = body["id"] if s == 201 else None
s, body = customer.call("DELETE", f"/api/customer/bookings/{bid2}")
check("Cancel pending booking removes it", s == 200 and body["bookingStatus"] == "DELETED", f"{s} {body}")
s, body = customer.call("DELETE", f"/api/customer/bookings/{bid}")
check("Cancel confirmed booking 4 days early -> full refund", s == 200 and float(body["refundAmount"]) > 0, f"{s} {body}")
s, body = customer.call("GET", "/api/customer/payments")
check("Payment history shows REFUNDED", s == 200 and body and body[0]["status"] == "REFUNDED", f"{body}")
s, wallet = customer.call("GET", "/api/customer/wallet")
check("Refund lands in the wallet as a REFUND row", s == 200
      and wallet["entries"][0]["type"] == "REFUND", f"{wallet}")

print("== 7. Member 6 - file handling and admin payment tools")
s, body = admin.call("POST", "/api/admin/files/import-movies", {})
check("Import movies file (duplicates skipped)", s == 200 and body["skipped"] >= 6, f"{s} {body}")
s, body = admin.call("POST", "/api/admin/files/import-users", {})
check("Import users file (duplicates skipped)", s == 200 and body["skipped"] >= 4, f"{s} {body}")
s, pays = admin.call("GET", "/api/admin/payments")
check("Admin sees all payments", s == 200)
refunded = [p for p in pays if p["status"] == "REFUNDED"]
if refunded:
    s, body = admin.call("DELETE", f"/api/admin/payments/{refunded[0]['id']}")
    check("Admin deletes refunded payment", s == 200, f"{s} {body}")

print("== 8. Member 1 - customer and admin management")
s, custs = admin.call("GET", "/api/admin/customers?q=kasun")
check("Admin searches customers", s == 200 and len(custs) >= 1)
s, body = customer.call("DELETE", "/api/customer/profile")
check("Customer with booking history cannot delete account", s == 400, f"{s} {body}")
s, body = customer2.call("DELETE", "/api/customer/profile")
check("Customer deletes own fresh account (no bookings)", s == 200, f"{s} {body}")
s, admins_list = admin.call("GET", "/api/admin/admins")
check("Admin list shows admins", s == 200 and len(admins_list) >= 2)
s, body = admin.call("POST", "/api/admin/admins", {"fullName": "New Staff", "email": f"s{uuid.uuid4().hex[:5]}@cinema.lk", "password": "staff999", "permissionLevel": "STAFF"})
check("Super admin creates an admin", s == 201, f"{s} {body}")

print("== 9. Cleanup of this run's test data (admin DELETE)")
s, body = admin.call("DELETE", f"/api/admin/bookings/{bid}")
check("Admin removes cancelled booking", s == 200, f"{s} {body}")
s, body = admin.call("DELETE", f"/api/admin/shows/{new_id}")
check("Admin deletes showtime", s == 200, f"{s} {body}")

print("== 10. Report export (file write)")
req = urllib.request.Request(BASE + "/api/admin/files/export-sales")
with admin.opener.open(req) as r:
    text = r.read().decode()
check("Sales report downloads as text", "SALES REPORT" in text and "Net sales" in text)


print("== 11. Tier 1 - promo codes as a pricing strategy")
# The show from section 5 was removed again in section 9, so the upgrade checks get their own.
up_start = (datetime.datetime.now() + datetime.timedelta(days=21)).replace(hour=18, minute=30, second=0, microsecond=0)
up_req = {"movieId": mid2, "screenId": screen_id, "startTime": up_start.isoformat(), "basePrice": 1800}
s, up_show = admin.call("POST", "/api/admin/shows", up_req)
for _ in range(30):
    if s == 201:
        break
    up_start = up_start + datetime.timedelta(days=1)
    up_req = {**up_req, "startTime": up_start.isoformat()}
    s, up_show = admin.call("POST", "/api/admin/shows", up_req)
up_id = up_show.get("id") if isinstance(up_show, dict) else None
check("A show for the upgrade checks is created", s == 201, f"{s} {up_show}")
s, promo = admin.call("GET", "/api/admin/promos")
check("Admin lists promo codes", s == 200 and len(promo) >= 2, f"status={s}")
code = "SMOKE" + uuid.uuid4().hex[:5].upper()
s, body = admin.call("POST", "/api/admin/promos", {"code": code, "type": "PERCENT", "value": 15,
                                                    "minSpend": 1000,
                                                    "validFrom": str(datetime.date.today()),
                                                    "validUntil": str(datetime.date.today() + datetime.timedelta(days=30)),
                                                    "maxUses": 100, "description": "smoke test"})
check("Admin creates a promo code", s in (200, 201) and body["code"] == code, f"{s} {body}")
promo_id = body.get("id") if isinstance(body, dict) else None
s, body = customer.call("POST", "/api/customer/promo/check", {"code": code, "total": 2000})
check("Promo check gives the discount", s == 200 and body["valid"] and float(body["discount"]) == 300.0, f"{body}")
s, body = customer.call("POST", "/api/customer/promo/check", {"code": code, "total": 100})
check("Promo check respects the minimum spend", s == 200 and body["valid"] is False, f"{body}")
s, body = customer.call("POST", "/api/customer/promo/check", {"code": "NO-SUCH-CODE", "total": 2000})
check("Unknown promo code is simply not valid", s == 200 and body["valid"] is False, f"{body}")

print("== 12. Tier 1 - seat bands and per-row pricing")
s, chosen = customer.call("GET", f"/api/shows/{up_id}")
bands = chosen.get("seatBands") or []
kinds = {b["band"] for b in bands}
check("Show exposes the band legend", len(bands) >= 3 and {"REGULAR", "PREMIUM", "VIP"} <= kinds, f"{bands}")
prices = {b["band"]: float(b["price"]) for b in bands}
check("Front rows are cheaper than back rows", prices.get("REGULAR", 0) < prices.get("VIP", 0), f"{prices}")

print("== 13. Tier 1 - analytics for the dashboard")
s, an = admin.call("GET", "/api/admin/analytics?days=30")
check("Analytics returns the KPI block", s == 200 and "revenue" in an and "occupancyPercent" in an, f"status={s}")
check("Analytics returns the funnel", s == 200 and set(an["funnel"]) >=
      {"seatHolds", "paidBookings", "cancelled", "checkedIn", "conversionPercent"}, f"{an.get('funnel')}")
check("Analytics returns revenue per screen and per film", s == 200
      and isinstance(an["revenueByScreen"], list) and isinstance(an["revenueByMovie"], list))
check("Analytics counts no-shows", s == 200 and "noShows" in an)

print("== 14. Tier 1 - paged admin lists")
s, page = admin.call("GET", "/api/admin/bookings/page?page=0&size=20")
check("Bookings page returns 20 rows and a total", s == 200 and len(page["content"]) <= 20 and page["totalElements"] > 0,
      f"status={s}")
s, page = admin.call("GET", "/api/admin/customers/page?page=0&size=10")
check("Customers page is paged too", s == 200 and "totalPages" in page, f"status={s}")

print("== 15. Tier 1 - ticket check-in at the door")
s, body = admin.call("POST", "/api/admin/tickets/validate", {"code": "BK-NOT-REAL"})
check("Unknown ticket code gives NOT_FOUND", s == 200 and body["result"] == "NOT_FOUND", f"{s} {body}")
s, today = admin.call("GET", "/api/admin/tickets/today")
check("Today's attendance summary loads", s == 200 and "expected" in today and "attendancePercent" in today, f"{s}")

print("== 16. Tier 2 - part of a booking can be given back")
s, part = customer.call("POST", "/api/customer/bookings", {"showId": up_id, "seatCodes": ["E1", "E2"]})
part_id = part.get("id") if s == 201 else None
if part_id:
    customer.call("POST", "/api/customer/payments",
                  {"bookingId": part_id, "method": "CARD", "reference": "4111111111111111"})
    s, body = customer.call("POST", f"/api/customer/bookings/{part_id}/cancel-seats", {"seatCodes": ["E2"]})
    check("Cancel one seat of two", s == 200 and float(body["refundAmount"]) > 0, f"{s} {body}")
    s, kept = customer.call("GET", f"/api/customer/bookings/{part_id}")
    check("The rest of the booking survives", s == 200 and kept["seatCodes"] == ["E1"] and kept["status"] == "CONFIRMED",
          f"{kept}")
    s, body = customer.call("POST", f"/api/customer/bookings/{part_id}/cancel-seats", {"seatCodes": ["E1"]})
    check("The last seat must be cancelled as a whole booking", s == 400, f"{s} {body}")
    customer.call("DELETE", f"/api/customer/bookings/{part_id}")

print("== 17. Tier 2 - a sold-out show takes a waitlist")
s, body = customer.call("POST", f"/api/customer/waitlist/{up_id}", {})
check("Waitlist refuses while seats are free", s == 400, f"{s} {body}")
s, body = customer.call("GET", "/api/customer/waitlist")
check("My waitlist is readable", s == 200 and isinstance(body, list), f"{s}")

print("== 18. Tier 2 - staff role (BOX_OFFICE) is fenced in")
door = Client()
s, _ = door.call("POST", "/api/auth/admin/login", {"email": "door@cinema.lk", "password": "door1234"})
check("Box-office account logs in", s == 200, f"status={s}")
s, body = door.call("POST", "/api/admin/tickets/validate", {"code": "BK-NOT-REAL"})
check("Box office may scan a ticket", s == 200 and body["result"] == "NOT_FOUND", f"{s} {body}")
s, body = door.call("GET", "/api/admin/customers")
check("Box office may not manage customers", s == 403, f"{s} {body}")
s, body = door.call("GET", "/api/admin/analytics?days=7")
check("Box office may not read the reports", s == 403
      and "box-office" in body["message"], f"{s} {body}")

print("== 19. Tier 3 - the API is documented")
s, docs = admin.call("GET", "/v3/api-docs")
check("OpenAPI document is served", s == 200 and "/api/admin/analytics" in docs["paths"], f"status={s}")
req = urllib.request.Request(BASE + "/swagger-ui/index.html")
with urllib.request.urlopen(req) as r:
    html = r.read().decode()
check("Swagger UI page is served", r.status == 200 and "Swagger" in html)

print("== 20. Tier 2 - the interface speaks three languages")
for lang in ("en", "si", "ta"):
    req = urllib.request.Request(BASE + f"/lang/{lang}.json")
    with urllib.request.urlopen(req) as r:
        table = json.loads(r.read().decode())
    check(f"Language file {lang}.json has the dictionary", r.status == 200 and len(table) >= 20, f"{len(table)} keys")

print("== 21. Cleanup of the upgrade test data")
if promo_id:
    s, body = admin.call("DELETE", f"/api/admin/promos/{promo_id}")
    check("Admin deletes the promo code", s == 200, f"{s} {body}")
if up_id:
    # The cancelled booking has to go before its showtime, which is itself a rule worth checking.
    if part_id:
        admin.call("DELETE", f"/api/admin/bookings/{part_id}")
    s, body = admin.call("DELETE", f"/api/admin/shows/{up_id}")
    check("Admin deletes the upgrade-check showtime", s == 200, f"{s} {body}")

print(f"\nRESULT: {PASSED} passed, {FAILED} failed")
sys.exit(1 if FAILED else 0)
