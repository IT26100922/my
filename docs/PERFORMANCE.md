# Performance: indexes, paging and the N+1 audit (Tier 3)

This note records what the upgrade changed so the system stays quick when the tables grow, and the
measurements behind each claim. Everything here was measured on the demo database that
`DemoHistorySeeder` produces: **5 000 bookings, 5 100 payments, 577 showtimes, 6 screens**.

## 1. Indexes on the tables that grow

An index is worth nothing until there is data, so the seeder is part of this work: without the demo
history a "before and after" would be two numbers near zero.

| Table | Index | The query it serves |
|---|---|---|
| `bookings` | `idx_booking_customer_created (customer_id, created_at)` | "My bookings", newest first |
| `bookings` | `idx_booking_show_status (show_id, status)` | which seats of this show are taken |
| `bookings` | `idx_booking_checked_in (checked_in_at)` | today's attendance, no-shows, the door page |
| `showtimes` | `idx_showtime_movie_start (movie_id, start_time)` | a film's timetable |
| `showtimes` | `idx_showtime_screen_start (screen_id, start_time)` | the overlap check when a show is created |
| `payments` | `idx_payment_method_paid (method, paid_at)` | revenue split by payment method |
| `payments` | `idx_payment_booking (booking_id)` | the payment of one booking (check-in, refunds) |
| `reviews` | `idx_review_movie_created (movie_id, created_at)` | a film's reviews |
| `reviews` | `idx_review_customer (customer_id)` | one review per booking |
| `notifications` | `idx_notification_customer_created (customer_id, created_at)` | the bell inbox |
| `notifications` | `idx_notification_unread (customer_id, read_at)` | the unread badge |
| `promo_codes` | `idx_promo_code (code)` unique | validating a code |
| `wallet_transactions` | `idx_wallet_customer_created (customer_id, created_at)` | the wallet page |
| `waitlist_entries` | `idx_waitlist_show_joined (show_id, joined_at)` + unique `(show_id, customer_id)` | who is waiting, in order |

The names match the `@Index` annotations on the entities (`Booking`, `Showtime`, `Payment`, `Review`,
`Notification`, `PromoCode`, `WalletTransaction`, `WaitlistEntry`), and `database/cinemax_mysql_setup.sql`
creates them with the same names (SECTION 3C, written as "add if missing" so the script can be re-run).

**Measured (`EXPLAIN`, `movie_booking` with 5 000/5 100 rows):**

```
-- seats of one show (used on every booking page load)
select seat_code from booking_seats bs join bookings b on b.id = bs.booking_id
 where b.show_id = 5 and b.status in ('CONFIRMED','CHECKED_IN');
→ b: ref on idx_booking_show_status, 9 rows, "Using index"

-- a film's timetable
select count(*) from showtimes s where s.movie_id = 1 and s.start_time >= now();
→ s: range on idx_showtime_movie_start, 6 rows (out of 577), "Using index"

-- the bell inbox of one customer
select count(*) from notifications n where n.customer_id = 30 and n.created_at >= '2026-10-01';
→ n: range on idx_notification_customer_created, "Using index"

-- revenue per payment method
select sum(p.amount - p.convenience_fee) from payments p
 where p.method = 'CARD' and p.paid_at >= '2026-10-01';
→ idx_payment_method_paid is offered to the optimizer (range, 2 903 of 5 100 rows);
  on a table this small MySQL sometimes prefers a full scan, which is why the index appears only in
  "possible_keys" - the index is there for the day the table is ten times bigger.
```

## 2. Paging instead of "send everything"

`GET /api/admin/bookings` still exists (the reports and the PDF export use it), but the admin screens now
ask for one page at a time:

| Endpoint | Returns |
|---|---|
| `GET /api/admin/bookings/page?page=0&size=20` | one `Page<BookingResponse>`: `content`, `totalElements`, `totalPages`, `number`, `size` |
| `GET /api/admin/customers/page?page=0&size=20` | the same for customers |

Both clamp `size` to 1..100 on the server, so a hand-written `?size=100000` cannot pull the table down.

**Measured over HTTP** (three runs, same server):

```
GET /api/admin/bookings                  → 2 509 080 bytes   0.32 - 0.38 s
GET /api/admin/bookings/page?size=20     →    10 320 bytes   0.29 - 0.38 s
```

The server-side time is similar at 5 000 rows (the join is cheap on a warm cache), but the response is
**243 times smaller** - 10 KB instead of 2.5 MB. That is what keeps the admin table usable in a browser:
the JSON is smaller to transfer, smaller to parse and 20 rows are all the DOM has to paint. At 500 000
bookings the difference stops being about transfer and becomes about the query itself, which is why the
`count` query and the `order by created_at desc` are the two things the index on `(customer_id, created_at)`
and the primary key serve.

## 3. The N+1 audit

A booking row is useless on its own: every screen needs the film, the theatre, the screen name, the
customer and the seat list. Loaded lazily, showing 20 bookings would fire 1 + 20 × 4 queries.

What was done:

* **`@EntityGraph` on the list queries** (`BookingRepository`): `show`, `show.movie`, `show.screen`,
  `show.screen.theatre`, `customer` and `seatCodes` come back with the booking row, so a page of 20
  bookings is one query, not eighty.
* **`seatCodes` is in the graph** (`@ElementCollection`). This was a real bug found by the audit: the
  sales export loaded the graph without `seatCodes`, and `exportSalesReport` then blew up with
  `LazyInitializationException` because the session had already closed (`spring.jpa.open-in-view=false`).
* **`spring.jpa.open-in-view=false`** - a lazy load outside a transaction now fails loudly in development
  instead of quietly firing 300 queries while the page renders.
* **`spring.jpa.properties.hibernate.default_batch_fetch_size=50`** - where a graph is not possible (single
  bookings, the demo seeder), lazy collections are fetched in batches of 50 ids instead of one query per
  row.
* **The analytics queries are aggregates**, not loops: one `group by` per chart (`revenueByMovie`,
  `revenueByScreen`, `revenueByDay`, `seatsByStartHour`, `methodMix`), and the funnel is five `count`
  queries. The page therefore costs a fixed handful of queries however many bookings exist. (The one
  exception is `findCheckedInSince(...).size()` for the checked-in figure; it is a single indexed query.)

**How to re-check it:** run the app with `logging.level.org.hibernate.SQL=DEBUG` (add it to
`application.properties` temporarily), open Admin → Bookings, and count the `select` lines: one for the
page, one for the count. If you see a stream of selects repeating the same join, a graph entry is missing.

## 4. The one-line request log

`config/RequestLogFilter` writes a single line per request, which is how the numbers above were traced:

```
2026-11-01T05:13:30.530+05:30  INFO ... c.moviebooking.config.RequestLogFilter : HTTP : GET / 200 51 ms user=-
```

Method, path, status, duration in milliseconds and the logged-in user. It is one line, so it can be read
in the console and counted with a `grep`.
