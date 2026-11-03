# CRUD by Team Member – SE1020 Movie Ticket Booking

Each member owns one component. Every component has its own entity, DTO, repository, service, controller and UI, and performs **Create, Read, Update and Delete** (CRUD). Customer and admin operations are listed separately because they use separate login portals and separate endpoints.

For each operation below:

- **Endpoint** is the API route.
- **What it does** explains the operation in plain words.
- **Rules** lists the checks the service enforces.
- **Why** explains the design reason.

Full API details are in README section 5.

**Shared front end.** Every member's screens use the same two stylesheets: `css/style.css` (layout and
component classes) and `css/studio.css` (the visual layer — glass surfaces, the 3D hero, type, motion).
No member adds page-specific CSS files, so a change made for one screen keeps the other five looking
like the same product.

---

## Member 1 – User & Authentication

**Classes:** `User` (abstract), `Customer`, `Admin`, `UserRole`, `CustomerService`, `AdminService`, `AuthService`, the auth strategies (`AuthStrategy`, `AbstractAuthStrategy`, `CustomerAuthStrategy`, `AdminAuthStrategy`), `CustomerProfileController`, `AdminUserController`, `AuthController`.

### Customer

**Create – register** `POST /api/auth/customer/register`

- **What it does:** creates a new customer account with name, email, password and optional phone.
- **Rules:** the email must be unique (case-insensitive). The password is stored only as a BCrypt hash.
- **Why:** customers register themselves, so no admin is needed to start booking.

**Read – profile** `GET /api/customer/profile`

- **What it does:** returns the logged-in customer's own details, including loyalty points.
- **Rules:** the customer is identified from the session, so a customer cannot read another customer's profile.
- **Why:** the session decides whose data is returned, so the URL never contains an ID to tamper with.

**Read – customer list (admin)** `GET /api/admin/customers?q=`

- **What it does:** lists all customers, or filters them by name or email when `q` is given.
- **Rules:** admin role only.
- **Why:** admins need to look up a customer for support or for the delete check.

**Read – one customer (admin)** `GET /api/admin/customers/{id}`

- **What it does:** returns one customer by ID.
- **Rules:** admin role only. Returns 404 if the customer does not exist.

**Update – profile** `PUT /api/customer/profile`

- **What it does:** changes the full name and phone. It can also change the password if a new one is given.
- **Rules:** the email cannot be changed after registration. The password is changed only when a new one is supplied.
- **Why:** the email is the login name, so keeping it fixed prevents accidental lock-outs.

**Delete – own account** `DELETE /api/customer/profile`

- **What it does:** deletes the logged-in customer's account and ends the session.
- **Rules:** blocked if the customer has any booking history.
- **Why:** deleting an account with bookings would break the booking and payment records. This is the same rule as the admin delete below.

**Delete – customer (admin)** `DELETE /api/admin/customers/{id}`

- **What it does:** deletes a customer account.
- **Rules:** blocked if the customer has booking history. Returns an error message explaining why.

### Admin

**Create – add admin** `POST /api/admin/admins`

- **What it does:** creates a new admin account with a full name, email, password (at least 6 characters) and permission level, SUPER_ADMIN or STAFF.
- **Rules:** only a SUPER_ADMIN can add admins. The email must be unique. The permission level must be one of the two allowed values.
- **Why:** only senior staff should be able to grant admin access.

**Read – admin list** `GET /api/admin/admins`

- **What it does:** lists all admin accounts with their permission levels.
- **Rules:** admin role only.

**Update – edit admin** `PUT /api/admin/admins/{id}`

- **What it does:** changes an admin's permission level (SUPER_ADMIN or STAFF).
- **Rules:** only a SUPER_ADMIN can edit admins. Name, email and password are not changed through this endpoint.
- **Why:** the permission level is the only setting an operator needs to change, so the update stays small and safe.

**Delete – remove admin** `DELETE /api/admin/admins/{id}`

- **What it does:** removes an admin account.
- **Rules:** only a SUPER_ADMIN can delete admins. An admin cannot delete their own account, so the system always keeps a way to log in.

**Login (separate portals)** `POST /api/auth/customer/login`, `POST /api/auth/admin/login`

- **What it does:** checks the email and password against the matching account type.
- **Why:** customers and admins use different pages and different endpoints, so a customer account cannot open the admin dashboard. `AuthInterceptor` checks the role on every request to `/api/customer/**` and `/api/admin/**`.

**UI pages:** `customer-register.html`, `customer-login.html`, `profile.html`, `admin-login.html`, and the *Customers* and *Admins* sections of `admin-dashboard.html`.

---

## Member 2 – Movie Catalogue

**Classes:** `Movie` (abstract), `StandardMovie`, `ImaxMovie`, `ThreeDMovie`, `MovieFactory`, `MovieRepository`, `MovieService`, `MovieController`, `MovieDto`.

**Create** `POST /api/admin/movies`

- **What it does:** adds a movie with its title, genre, language, duration, rating, release date, description, format and details (director, producers, writers, cast, trailer link, scene images).
- **Rules:** the title must be unique (case-insensitive). The duration must be at least 30 minutes. The format must be STANDARD, IMAX or THREE_D.
- **Why:** `MovieFactory` creates the correct subclass for the format, so the ticket surcharge (IMAX +LKR 300, 3D +LKR 200, Standard 0) is decided by the object type, not by an `if` statement.

**Read – list** `GET /api/movies` (filters `title`, `genre`)

- **What it does:** returns the movie list, optionally filtered by title or genre. Public, so visitors can browse without logging in.
- **Why:** the catalogue is the first thing a customer sees.

**Read – details** `GET /api/movies/{id}`

- **What it does:** returns one movie with its credits, trailer link and scene images, used by `movie.html`.
- **Why:** the details page needs everything about one film in a single request.

**Update** `PUT /api/admin/movies/{id}`

- **What it does:** changes the movie's details.
- **Rules:** the format cannot be changed after creation. The error message tells the admin to create a new movie instead.
- **Why:** JPA cannot change an object's subclass in place. Changing the format would also change the surcharge that existing bookings were priced with.

**Delete** `DELETE /api/admin/movies/{id}`

- **What it does:** removes a movie.
- **Rules:** blocked while any showtime still uses the movie.
- **Why:** deleting a movie with showtimes would leave those showtimes without a film.

**UI pages:** `index.html` (catalogue), `movie.html` (details, credits, trailer and scenes, showtimes), and the *Movies* section of the admin dashboard.

**Import link:** `FileService.importMovies()` copies rows out of the MySQL table `import_movies` and creates movies through the same service, so the same rules apply to imported films.

---

## Member 3 – Theatre & Screen

**Classes:** `Theatre`, `Screen` (abstract), `StandardScreen`, `PremiumScreen`, `VipScreen`, `ScreenFactory`, `TheatreService`, `ScreenService`, `TheatreController`, `ScreenController`, `TheatreDto`, `ScreenDto`.

### Theatre

**Create** `POST /api/admin/theatres`

- **What it does:** adds a theatre with its name, city, address and phone.
- **Why:** each screen belongs to a theatre, so theatres are created first.

**Read** `GET /api/theatres`, `GET /api/theatres/{id}`

- **What it does:** lists theatres or returns one theatre. Public.
- **Why:** customers choose a theatre before they choose a showtime.

**Update** `PUT /api/admin/theatres/{id}`

- **What it does:** changes the theatre's name, city or address.
- **Rules:** no restriction, because the theatre's details do not affect bookings directly.

**Delete** `DELETE /api/admin/theatres/{id}`

- **What it does:** removes a theatre.
- **Rules:** blocked while the theatre still has screens.
- **Why:** removing a theatre with screens would leave those screens without a building.

### Screen

**Create** `POST /api/admin/screens`

- **What it does:** adds a screen to a theatre with its name, screen type, number of rows and seats per row.
- **Rules:** the type must be STANDARD, PREMIUM or VIP. Rows are letters (maximum 26). Seats per row are numbers (maximum 30).
- **Why:** `ScreenFactory` creates the correct subclass for the type. Each type has its own price multiplier (Standard ×1.00, Premium ×1.30, VIP ×1.80), chosen by polymorphism.

**Read** `GET /api/screens?theatreId=`, `GET /api/screens/{id}`

- **What it does:** lists the screens of a theatre, or returns one screen.
- **Why:** the showtime form needs the screens of a chosen theatre.

**Update** `PUT /api/admin/screens/{id}`

- **What it does:** changes the screen's name, theatre, number of rows or seats per row.
- **Rules:** the type cannot be changed after creation. The seat layout cannot change while the screen has showtimes.
- **Why:** the type fixes the price multiplier that existing showtimes use. Changing the seats while bookings exist could make booked seats disappear.

**Delete** `DELETE /api/admin/screens/{id}`

- **What it does:** removes a screen.
- **Rules:** blocked while the screen has showtimes.

**UI pages:** `theatres.html` (public list of theatres and screens), and the *Theatres* and *Screens* sections of the admin dashboard.

---

## Member 4 – Showtime Scheduling

**Classes:** `Showtime`, `ShowtimeRepository`, `ShowService`, `ShowController`, `ShowDto`, `PricingStrategy` (interface), `WeekdayPricingStrategy`, `WeekendPricingStrategy`, `MatineePricingStrategy`.

**Create** `POST /api/admin/shows`

- **What it does:** schedules a movie on a screen at a start time with a base price. The end time is calculated from the movie's duration.
- **Rules:**
    - The start time must be in the future.
    - No other show on the same screen may overlap this time. The check uses the start and end times of both shows.
- **Why:** a screen can only show one film at a time, and the overlap check prevents double-booking the room.

**Read – public list** `GET /api/shows?movieId=&date=`, `GET /api/shows/{id}`

- **What it does:** returns the showtimes for a movie or a date, with the seat price and the list of seats already held.
- **Why:** customers need to see upcoming times and how many seats are left.

**Read – admin list** `GET /api/admin/shows`

- **What it does:** returns all showtimes, including past ones, for the admin dashboard.

**Update** `PUT /api/admin/shows/{id}`

- **What it does:** changes the movie, screen, start time or base price.
- **Rules:** the same future-time and overlap checks apply, excluding this showtime itself, so it does not clash with its own old time.
- **Why:** an admin may need to move a show, and the check must still apply after the change.

**Delete** `DELETE /api/admin/shows/{id}`

- **What it does:** removes a showtime.
- **Rules:** blocked once any booking exists for the showtime.
- **Why:** the bookings and payments depend on this showtime's price and time.

**Pricing rule (not a CRUD operation, but part of this component):**

- Seat price = base price × screen multiplier + movie surcharge.
- Then the rule with the highest priority applies: matinee before 12:00 (×0.80), weekend Saturday and Sunday (×1.20), otherwise weekday (×1.00).
- **Why:** each rule is a separate class that implements the same `PricingStrategy` interface, so a new rule can be added without changing the code that calls them.

**UI pages:** the showtime list in `movie.html`, and the *Showtimes* section of the admin dashboard.

---

## Member 5 – Booking & Seat Reservation

**Classes:** `Booking`, `BookingStatus`, `BookingRepository`, `BookingService`, `BookingController`, `BookingDto`, `CancellationPolicy` (interface), `FullRefundPolicy`, `PartialRefundPolicy`, `NoRefundPolicy`.

**Create** `POST /api/customer/bookings`

- **What it does:** reserves chosen seats for a showtime. The booking starts as PENDING_PAYMENT and gets a reference code (BK followed by 8 characters).
- **Rules:**
    - The showtime must not have started yet.
    - At least one seat is required, and at most 10 seats per booking.
    - Seat codes must be valid (a letter row and a number, inside the screen's layout).
    - A seat cannot be chosen twice in the same request.
    - A seat already held by a PENDING_PAYMENT or CONFIRMED booking cannot be taken.
- **Why:** the reservation holds the seats until payment, so two customers cannot buy the same seat.

**Read – my bookings** `GET /api/customer/bookings`, `GET /api/customer/bookings/{id}`

- **What it does:** lists the logged-in customer's bookings (newest first), or returns one booking.
- **Rules:** a customer can only see their own bookings. Another customer's booking returns "not found".
- **Why:** the booking ID alone must not reveal someone else's booking.

**Read – all bookings (admin)** `GET /api/admin/bookings`

- **What it does:** lists every booking, newest first.

**Update – change seats** `PUT /api/customer/bookings/{id}`

- **What it does:** replaces the seats of a booking and recalculates the total.
- **Rules:** allowed only while the booking is PENDING_PAYMENT. The same seat checks as creation apply, ignoring this booking's own old seats.
- **Why:** after payment the seats are fixed, because the payment amount was already taken.

**Delete – cancel (customer)** `DELETE /api/customer/bookings/{id}`

- **What it does:** cancels the booking.
    - **Awaiting payment:** the booking is deleted and the seats are released. No money was taken.
    - **Confirmed:** the cancellation policy decides the refund, the payment is refunded by that amount, and the booking is marked CANCELLED.
- **Rules:** a booking that is already cancelled cannot be cancelled again.
- **Refund policy for confirmed bookings:**
    - 24 hours or more before the show: 100% refund (`FullRefundPolicy`).
    - 2 to 24 hours before the show: 50% refund (`PartialRefundPolicy`).
    - Under 2 hours before the show: no refund (`NoRefundPolicy`).
- **Why:** the policy is a separate class for each rule, so the refund rules can change without touching the cancel logic.

**Delete – remove cancelled booking (admin)** `DELETE /api/admin/bookings/{id}`

- **What it does:** removes a cancelled booking and its payment records.
- **Rules:** only CANCELLED bookings can be deleted. Active bookings are protected.
- **Why:** this keeps the sales records clean while still protecting bookings that customers are using.

**UI pages:** `booking.html` (seat map, edit mode for changing seats), `my-bookings.html` (list and cancel), and the *Bookings* section of the admin dashboard.

---

## Member 6 – Payment & File Handling

**Classes:** `Payment`, `PaymentStatus`, `PaymentMethodType`, `PaymentRepository`, `PaymentService`, `PaymentController`, `PaymentDto`, `PaymentProcessor` (interface), `AbstractPaymentProcessor`, `CardPaymentProcessor`, `WalletPaymentProcessor`, `CashCounterPaymentProcessor`, `FileService`, `FileController`, `FileDto`.

**Create – pay** `POST /api/customer/payments`

- **What it does:** pays for a PENDING_PAYMENT booking. The booking becomes CONFIRMED, and the customer earns one loyalty point per seat.
- **Rules:**
    - The booking must belong to the logged-in customer and must be awaiting payment.
    - The payment method decides the validation and fee:
    - **Card:** 16 digits, 2% fee. Only the last four digits are stored.
    - **Wallet:** ID of 5 to 40 characters, no fee.
    - **Cash counter:** code in the form `CNT-NN`, no fee.
- **Why:** each method is a separate processor class that extends `AbstractPaymentProcessor`. The abstract class has a fixed sequence in `charge()` (template method): validate the reference, calculate the fee, create the transaction reference, then build the receipt. Each subclass supplies only the parts that differ, such as its validation rule and fee. Callers use only `charge()`, through the `PaymentProcessor` interface.

**Read – my payments** `GET /api/customer/payments`

- **What it does:** lists the customer's own payment history, newest first.

**Read – all payments (admin)** `GET /api/admin/payments`

- **What it does:** lists all payments, newest first. This is the transaction history, so payments on cancelled bookings stay in the list (marked CANCELLED).
- **Rules:** the sales analytics (dashboard totals, daily chart, payment methods, top movies, CSV export and the sales report) leave out payments on cancelled bookings. Only the history list shows them.

**Update – manual refund (admin)** `PUT /api/admin/payments/{id}`

- **What it does:** records a full manual refund, for example cash returned at the counter. The payment becomes REFUNDED and the booking is CANCELLED.
- **Rules:** only the REFUNDED status is accepted here. A payment that is already fully refunded is rejected.
- **Why:** automatic refunds happen during customer cancellation. This endpoint covers refunds that the system did not make itself.

**Delete – remove refunded payment (admin)** `DELETE /api/admin/payments/{id}`

- **What it does:** removes a payment record.
- **Rules:** only REFUNDED payments can be deleted. Successful payments are kept as records.
- **Why:** a paid transaction must stay in the ledger, so only refunded ones can be removed.

### File handling

- **Read – import movies** `POST /api/admin/files/import-movies` reads the MySQL table `import_movies` (one row per movie) and inserts the new ones through `MovieService`. Movies whose title already exists are skipped, and a bad row is reported in the `errors` list without stopping the rest of the batch.
- **Read – import users** `POST /api/admin/files/import-users` reads the MySQL table `import_users`. `password_hash` may hold a BCrypt hash (inserted as it is) or a plain password (hashed on the way in), so no readable password is ever stored.
- **Read – import showtimes** `POST /api/admin/files/import-showtimes` reads the MySQL table `import_showtimes` and inserts screenings into the `showtimes` table, linking each one to an existing movie, theatre and screen by name. Past shows and clashes are skipped; staged shows that are all in the past are moved forward by whole days so the timetable keeps its shape.
- **Write – sales report, PDF** `GET /api/admin/reports/sales.pdf` (`ReportPdfService`, OpenPDF) builds a two-page report: page 1 has the KPI boxes, a daily-revenue bar chart, a payment-method donut with legend and the top-five-movies bars; page 2 repeats every figure as tables (summary, revenue by movie with share, by payment method, daily net, two-up so a full month fits). The charts are vector drawing commands, so they stay sharp at any zoom.
- **Write – sales report, CSV** `GET /api/admin/reports/sales.csv` exports the same period as one spreadsheet-friendly file with four sections: `SUMMARY`, `DAILY`, `BY_MOVIE` and `BY_PAYMENT_METHOD`. Numbers are written with two decimals and no thousands separator, so Excel reads them as numbers. A CSV file cannot contain a picture, so the charts are drawn *inside the cells*: every row of `DAILY`, `BY_MOVIE` and `BY_PAYMENT_METHOD` ends with a `bar` column built from block characters (`█ ▉ ▊ ▌ ▍`), scaled to the best day / movie / method, plus `share_percent` for the last two. `SUMMARY` carries a `daily_net_sparkline` cell (`▁▂▃▄▅▆▇█`) that shows the whole period at a glance. The file starts with a UTF-8 byte-order mark so Excel on Windows renders the blocks, and the `bar` column only looks like a chart when that column uses a monospace font (Consolas or Courier New).
- **Write – timestamped text report** `GET /api/admin/files/export-sales` writes a `.txt` report into `data/reports/` and returns it as a download. Money totals in all three report formats exclude payments on cancelled bookings.
- **Write – import tables kept in step** after an admin adds, edits or deletes a movie or showtime, `POST /api/admin/files/export-movies` and `/export-showtimes` (or the same calls made by the admin portal) rewrite `import_movies` / `import_showtimes` from the live tables. A failure there is logged and does not undo the saved change.
- **Read – import status** `GET /api/admin/files/status` returns the row counts of the three import tables and of `movies`, `users` and `showtimes`, which is what the cards display under each button.
- **Data storage:** the application data is stored in MySQL (database `movie_booking`, schema created automatically on first start). The seed text files are used for the import and export described above.
- **Why:** the course requires file reading and writing. The import uses the same services as the web forms, so imported data follows the same rules.

**UI pages:** the payment step in `booking.html`, and the *Payments* and *Files & Reports* sections of the admin dashboard. *Files & Reports* has four cards: **Import movies**, **Import users**, **Import showtimes** (all write straight into the MySQL tables) and **Sales report**, which offers the **PDF** and **CSV** downloads side by side.

---

## Summary

| Member | Component | Create | Read | Update | Delete |
|---|---|---|---|---|---|
| 1 | User & Authentication | Customer register; admin add (SUPER_ADMIN) | Own profile; admin lists customers and admins | Own profile; admin edit (SUPER_ADMIN) | Own account; admin deletes customer (no bookings); admin deletes admin (SUPER_ADMIN) |
| 2 | Movie Catalogue | Admin adds movie | Movie list and details | Admin edits movie (format fixed) | Admin deletes movie (no showtimes) |
| 3 | Theatre & Screen | Admin adds theatre and screen | Theatre and screen lists | Admin edits theatre and screen (type fixed) | Admin deletes theatre (no screens) and screen (no showtimes) |
| 4 | Showtime Scheduling | Admin adds showtime (overlap check) | Public list and admin list | Admin edits showtime (same checks) | Admin deletes showtime (no bookings) |
| 5 | Booking & Seats | Customer books seats | My bookings; admin sees all | Customer changes seats (pending payment only) | Customer cancels (refund policy); admin deletes cancelled bookings |
| 6 | Payment & Files | Customer pays (card, wallet or cash counter) | My payments; admin sees all | Admin records a manual refund | Admin deletes refunded payments; file import and sales-report export |
