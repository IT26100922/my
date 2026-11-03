# CineMax — upgrade roadmap

> **Status: Tier 1, 2 and 3 are built.** This document was written as a plan; it is kept as the record of
> what each item was meant to prove. Every Tier 1–3 item below now exists in the code (the class or page is
> named in the item), the evidence lives in `README.md` §3 and in `docs/PERFORMANCE.md` /
> `docs/ACCESSIBILITY.md`, and Tier 4 is still open. Nothing in this file is a promise for a future session:
> where an item says "will add", read "adds", because it is done.

Suggestions for the next version of the system. Each one is written against **this** codebase: it names
the class or file that already does half the work, so you can judge the real cost. Effort is for one
person who knows the code; "marks" means what a marker sees in a 10-minute demo.

Nothing here breaks the rules the project already follows: MySQL only, Java 17, no front-end libraries,
`screentype` / `format` stay the JPA discriminators, `MAX_SEATS_PER_BOOKING = 10`, refunds
24 h 100 % / 2–24 h 50 % / <2 h 0 %.

---

## Tier 1 — do these first (high marks per hour)

### 1. Ticket check-in at the door  ·  effort M  ·  marks ★★★★★
You already generate a QR per booking (`ticket/service/TicketService.java`, zxing) but nothing ever reads
it. A cinema's job is not finished when the ticket is printed.

* `POST /api/admin/tickets/validate` with the ticket code → sets `BookingStatus.CHECKED_IN`,
  stores `checkedInAt`, `checkedInBy`.
* New `admin-scan.html` page: a big input box (works with a USB barcode scanner — they type and press
  Enter), green "valid" panel with the seat list, red "already used at 19:42" panel, amber "wrong day".
* Rejects: cancelled, unpaid, wrong showtime, second scan.
* Report line: attendance vs no-shows per showtime — you already compute occupancy in
  `report/service/ReportService.java`, this adds the other half.

Why it is worth it: it is the only feature in this list that a real cinema would pay for tomorrow, and it
demos in 30 seconds with the laptop and a phone.

### 2. Database indexes + pagination  ·  effort S  ·  marks ★★★★
There is not one `@Index` in the project, and the admin lists call
`findAllByOrderByCreatedAtDesc()` — fine with 36 showtimes, slow with 36 000 bookings.

* `@Table(indexes = …)` on `Booking` (`showtime_id, seat_code`), `Showtime` (`movie_id, starts_at`),
  `Payment` (`booking_id`), `Review` (`movie_id`), `Notification` (`customer_id, created_at`).
* `Pageable` on the admin endpoints (`/api/admin/bookings`, users, movies) + "‹ 1 2 3 ›" in the tables.
* Add a seed script with, say, 5 000 bookings so the before/after is measurable — showing a query plan
  (`EXPLAIN`) before and after is an easy way to earn an "understands the database" comment.

### 3. Promo codes  ·  effort S–M  ·  marks ★★★★
`show/pricing/PricingStrategy.java` already has three implementations (matinee / weekday / weekend). A
promo code is a fourth strategy, so the pattern you were taught gets used for real.

* `PromoCode` entity: `code`, `type` (PERCENT / FLAT), `value`, `minSpend`, `validFrom`, `validTo`,
  `maxUses`, `usedCount`, `active`.
* `PromoPricingStrategy` applied after the base price; `POST /api/customer/bookings` accepts `promoCode`.
* Booking row stores `discount_amount` + the code, so the PDF, the sales report and the refund maths all
  stay correct (refund on the **paid** amount, not the list price).
* Admin CRUD page (reuse the movies table pattern).

### 4. "Notify me" waitlist for sold-out shows  ·  effort M  ·  marks ★★★★
Every other booking site does this and yours has the plumbing already.

* `WaitlistEntry` (showtime, customer, createdAt, notifiedAt).
* When a cancel frees seats (`BookingService` cancel path), the first N waiters get a
  `NotificationType.WAITLIST_OPEN` notification plus a 30-minute priority window.
* One button on the showtime list when the show is full; the notification page already exists.

### 5. Analytics pass on the admin dashboard  ·  effort M  ·  marks ★★★★
You have `charts.js` and a PDF report; add the four numbers a manager asks for:

* revenue per screen and per movie (this week vs last),
* occupancy % by showtime slot ("19:00 sells out, 11:00 does not"),
* funnel: movie views → seat holds → paid bookings → cancellations (a holds table with an outcome column
  gets you this),
* no-show rate once feature 1 exists.

---

## Tier 2 — features that widen the product

### 6. Seat bands (Regular / Premium / VIP inside one screen)  ·  effort M  ·  marks ★★★★
`theatre/entity/` already has `PremiumScreen`, `VipScreen`, `StandardScreen` — but the *seat map inside a
screen* is one price. Give rows a band (`A–C` VIP, `D–H` premium, rest regular), colour the seat map by
band, price the selection per row. It ties the screen hierarchy and the pricing strategies together in a
way a marker can see in one screen.

### 7. Wallet with a real ledger  ·  effort M  ·  marks ★★★
`PaymentMethodType.WALLET` exists but there is no wallet. Add `WalletTransaction` (top-up, payment,
refund) and a balance on `Customer`. Instant refunds to the wallet, card refunds "3–5 working days" —
the 24 h / 2–24 h rule stays exactly as it is, only the destination changes.

### 8. Sinhala / Tamil / English UI  ·  effort M  ·  marks ★★★★
A local product that only speaks English is the easiest thing for a panel to criticise. One `lang/en.json`,
`si.json`, `ta.json`, `data-i18n` attributes on the 16 pages, a switch in the topbar, `localStorage` like
the theme. No library needed. Do the customer pages first (home, movie, booking, my bookings), admin last.

### 9. Staff role for the counter  ·  effort S–M  ·  marks ★★★
`UserRole` is ADMIN or CUSTOMER. Add `BOX_OFFICE`: can sell seats and check tickets in, cannot edit
movies or read reports. The check is centralised in `config/AuthInterceptor.java`, so this is a small,
clean change — and it makes the check-in feature usable by a real usher.

### 10. Cancel or move part of a booking  ·  effort M  ·  marks ★★★
Today it is all-or-nothing (`booking/controller/BookingController.java`). "Cancel 2 of my 4 seats, keep
the other 2" is the request real customers make. Same refund policy applied per seat, one new endpoint,
one dialog.

---

## Tier 3 — engineering quality (what a good panel probes)

| Upgrade | Effort | Why |
|---|---|---|
| **Concurrency test for the seat lock** — two threads booking the same seat, assert exactly one succeeds | S | `BookingService` uses a pessimistic lock; a JUnit test that proves it is worth more than a paragraph in the report |
| **MockMvc controller tests** — validation errors, 401/403, 409 on a taken seat | M | 41 tests now, all service/flow level; the web layer is untested |
| **OpenAPI / Swagger UI** (`springdoc-openapi-starter-webmvc-ui`) | S | 78 endpoints become a browsable page for the demo. Adds one dependency — check with your lecturer first |
| **`@EntityGraph` / N+1 audit** over showtime → movie → screen → theatre | S | Turn on `spring.jpa.show-sql` and count queries on the admin bookings list |
| **Structured request log** — one line per request with method, path, status, ms, user | S | Cheap, and the console looks professional in a demo |
| **GitHub Actions CI** — `mvn test` against a MySQL service container, plus the smoke test | S–M | "It builds on my machine" stops being a risk; you get a green badge for the report |
| **Docker Compose** — MySQL + app, one command | M | Same reason; also fixes DB password drift between machines |
| **Accessibility + Lighthouse pass** — focus order, dialog labels, contrast, image sizes | S | Free marks in most rubrics |

---

## Tier 4 — stretch, if there is time

* **Real gateway in sandbox mode** behind the existing `PaymentProcessor` interface (Stripe / PayHere
  sandbox). Proves the abstraction was worth having.
* **Occupancy-based dynamic pricing** — a fifth `PricingStrategy`: the last 20 seats cost more, Tuesday
  matinée costs less. You can show the same seat at three prices across three days.
* **"Also booked" recommendations** — one `GROUP BY` co-occurrence query on bookings, no ML needed.
* **Load test** — k6 or JMeter, 50 virtual users buying the same showtime; screenshot the p95 latency and
  the zero double-bookings in the report.
* **Queue position screen** for high-demand releases (a waiting room before the seat map). Impressive,
  but only if features 1–5 are already solid.

---

## What I would do with one week

1. **Day 1–2** — indexes + pagination + the 5 000-booking seed (the demo gets fast and you have numbers).
2. **Day 3–4** — ticket check-in (`/api/admin/tickets/validate` + `admin-scan.html`), because it is the
   best 30 seconds of the presentation.
3. **Day 5** — promo codes on top of the existing pricing strategies.
4. **Day 6** — concurrency test + MockMvc tests + CI workflow, so all of the above is evidenced.
5. **Day 7** — README/Docs update and the extra slides: one before/after table (query time, test count,
   features) is what people remember.

## What to avoid

* Don't add a second database, a message broker, Redis or a JS framework. None of it earns marks here and
  all of it makes the "run it on my laptop" step fragile.
* Don't rewrite the theme again. The design is done; spend the time on features a marker can click.
* Don't change `format` / `screentype` on an existing row (JPA discriminator, immutable) and don't soften
  the refund rule to make a demo easier — those two are what the current code is most careful about.
