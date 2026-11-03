# Team Workload – SE1020 Movie Ticket Booking (6 members)

Each member owns **one component** with its own entity, DTOs, repository, service, controller, UI pages and OOP concepts. Each component includes **four CRUD operations**, which exceeds the minimum of three. Every member explains their component in the viva.

Members commit to Git from their own accounts. See README section 7.

---

## Summary table

| # | Member | Component | Entities / key classes | CRUD (4 ops) | OOP concepts demonstrated | UI pages | Files / data |
|---|---|---|---|---|---|---|---|
| 1 | **Member 1** | User & Authentication | `User` (abstract), `Customer`, `Admin`, `AuthStrategy`, `AbstractAuthStrategy`, `CustomerAuthStrategy`, `AdminAuthStrategy`, `AuthService`, `CustomerService`, `AdminService`, `AuthInterceptor` | **C** register customer · **R** profile + admin customer search · **U** profile update, admin role update · **D** delete customer (no bookings) or admin (SUPER_ADMIN) | Inheritance (User → Customer/Admin), abstraction (AuthStrategy), template method (AbstractAuthStrategy), strategy pattern, information hiding (password hash never returned), BCrypt | `customer-login.html`, `customer-register.html`, `admin-login.html`, `profile.html`, admin **Customers** and **Admins** sections | `import_users` (MySQL) |
| 2 | **Member 2** | Movie Catalogue | `Movie` (abstract), `StandardMovie`, `ImaxMovie`, `ThreeDMovie`, `MovieFactory`, `MovieService`, `MovieController`, `MovieDto` | **C** add movie · **R** list/search by title and genre, details · **U** edit movie (format locked) · **D** delete movie (no showtimes) | Inheritance and polymorphism (`getSurcharge()`, `getFormat()`), factory pattern, validation annotations, encapsulation | `index.html` (search and poster grid), `movie.html` (details), admin **Movies** section | `import_movies` (MySQL) |
| 3 | **Member 3** | Theatre & Screen | `Theatre`, `Screen` (abstract), `StandardScreen`, `PremiumScreen`, `VipScreen`, `ScreenFactory`, `TheatreService`, `ScreenService`, seat-code validation | **C** theatre and screen · **R** theatre list with screens · **U** edit (type locked, layout locked while showtimes exist) · **D** delete (blocked by showtimes or screens) | Inheritance and polymorphism (`getPriceMultiplier()`), factory pattern, composition (Theatre has Screens), encapsulation of seat rules | `theatres.html`, admin **Theatres** and **Screens** sections | Seeded in `DataInitializer` |
| 4 | **Member 4** | Showtime Scheduling & Pricing | `Showtime`, `PricingStrategy` (interface), `WeekdayPricingStrategy`, `WeekendPricingStrategy`, `MatineePricingStrategy`, `ShowService`, overlap check | **C** schedule show · **R** shows by movie and date, live seat availability · **U** reschedule or reprice · **D** delete (blocked if bookings exist) | Strategy pattern (pricing rules chosen by priority), polymorphism, abstraction, business rules (no overlaps, future start time) | `movie.html` (showtime picker), admin **Showtimes** section | Seeded showtimes in `DataInitializer` |
| 5 | **Member 5** | Booking & Seat Reservation | `Booking`, `BookingStatus`, `CancellationPolicy` (interface), `FullRefundPolicy`, `PartialRefundPolicy`, `NoRefundPolicy`, `BookingService`, `BookingController` | **C** reserve seats · **R** my bookings, booking details · **U** change seats while pending · **D** cancel (refund by policy) / admin deletes cancelled bookings | Strategy pattern (cancellation policies), polymorphism, encapsulation of booking state transitions, concurrency check (held seats) | `booking.html` (seat map), `my-bookings.html`, admin **Bookings** section | – |
| 6 | **Member 6** | Payment & File Handling | `Payment`, `PaymentStatus`, `PaymentMethodType`, `PaymentProcessor` (interface), `AbstractPaymentProcessor` (template method), `CardPaymentProcessor`, `WalletPaymentProcessor`, `CashCounterPaymentProcessor`, `PaymentService`, `FileService`, `FileController` | **C** pay · **R** payment history (customer and admin) · **U** record manual refund (admin) · **D** delete refunded payment (admin) · **Import READ** the MySQL tables `import_movies` / `import_users` / `import_showtimes` into the live tables · **File WRITE** sales report as PDF (charts) and CSV | Template method (`charge()` fixed, steps overridden), strategy pattern (payment methods), abstraction, polymorphism, information hiding (only last 4 card digits stored), JDBC/JPA access to the MySQL import tables, file I/O (java.nio Files) for writing the timestamped report | `booking.html` (payment panel), admin **Payments** and **Files & Reports** sections | `import_movies`, `import_users`, `import_showtimes` (read), `data/reports/*.txt` (written) |

### Shared responsibilities

Each member contributes to shared areas with care to avoid conflicts:

| Shared area | Owner for review |
|---|---|
| `config/DataInitializer.java` (seed data) | Member 3 (theatres) with input from Members 2 and 4 |
| `exception/GlobalExceptionHandler.java` | Member 1 |
| `static/css/style.css`, `static/js/common.js` (design system and helpers) | Member 5 |
| `static/js/admin.js` (admin dashboard) | Member 6 (each member maintains their own section config in the `SECTIONS` list) |
| `README.md`, `docs/` | Everyone writes their own part. The team lead merges. |

---

## Member details

### Member 1 – User & Authentication
- **Classes:** `User` (abstract), `Customer`, `Admin`, `UserRole`, `AuthStrategy`, `AbstractAuthStrategy`, `CustomerAuthStrategy`, `AdminAuthStrategy`, `AuthService`, `CustomerService`, `AdminService`, `AdminUserController`, `AuthController`, `CustomerProfileController`, `AuthInterceptor`.
- **Key rules:** separate login endpoints for customers and admins; role checks by interceptor; only SUPER_ADMIN can create, update or delete admins; a customer with booking history cannot be deleted.
- **Viva talking points:** why `User` is abstract; how the Strategy and Template Method patterns separate customer and admin login; how the password hash is hidden from responses.

### Member 2 – Movie Catalogue
- **Classes:** `Movie` (abstract), `StandardMovie`, `ImaxMovie`, `ThreeDMovie`, `MovieFactory`, `MovieService`, `MovieController`, `MovieDto`.
- **Key rules:** surcharge depends on format (Standard 0, 3D +200, IMAX +300); format cannot change after creation; search by title and genre.
- **Viva talking points:** how JPA single-table inheritance stores the format; why the factory picks the subclass from a format code.

### Member 3 – Theatre & Screen
- **Classes:** `Theatre`, `Screen` (abstract), `StandardScreen`, `PremiumScreen`, `VipScreen`, `ScreenFactory`, `TheatreService`, `ScreenService`, `ScreenRepository`.
- **Key rules:** 1–26 rows, 1–30 seats per row; seat codes are a letter followed by a number; screen type cannot change; layout cannot change while showtimes exist.
- **Viva talking points:** how each screen type's price multiplier is a polymorphic method; how composition links theatres and screens.

### Member 4 – Showtime Scheduling & Pricing
- **Classes:** `Showtime`, `PricingStrategy`, `WeekdayPricingStrategy`, `WeekendPricingStrategy`, `MatineePricingStrategy`, `ShowService`.
- **Key rules:** start time must be in the future; no overlaps on the same screen; end time is calculated from movie duration; seat price = base × screen multiplier + movie surcharge, then the pricing strategy with the highest priority applies.
- **Viva talking points:** how strategies are chosen by priority; how a new pricing rule can be added without changing `ShowService`.

### Member 5 – Booking & Seat Reservation
- **Classes:** `Booking`, `BookingStatus`, `CancellationPolicy`, `FullRefundPolicy`, `PartialRefundPolicy`, `NoRefundPolicy`, `BookingService`, `BookingController`, `BookingDto`.
- **Key rules:** at most 10 seats per booking; a seat held by a pending or confirmed booking cannot be taken again; seats can be changed only while the booking is pending; cancellation refunds follow the time-based policy.
- **Viva talking points:** how the seat map and the held-seat check prevent double booking; how the refund policy is picked from hours before the show.

### Member 6 – Payment & File Handling
- **Classes:** `Payment`, `PaymentStatus`, `PaymentMethodType`, `PaymentProcessor`, `AbstractPaymentProcessor`, `CardPaymentProcessor`, `WalletPaymentProcessor`, `CashCounterPaymentProcessor`, `PaymentCharge`, `PaymentService`, `FileService`, `FileController`, `ImportResult`.
- **Key rules:** card payments take a 2% fee and store only the last 4 digits; wallet IDs are 5–40 characters with no fee; counter codes are in the form `CNT-NN`. A successful payment confirms the booking and adds loyalty points. Refunds can be recorded only by admins. Payments on cancelled bookings stay in the payment history but are left out of all sales figures, charts and the sales report.
- **File handling:** `FileService` reads `movies.txt`, `users.txt` and `showtimes.txt` (pipe-separated, duplicates skipped). It writes a timestamped sales report to `data/reports/` (also returned as a download) and rewrites `movies.txt` and `showtimes.txt` after admin changes, so the seed files match the database.
- **Data storage:** the database is MySQL (`movie_booking`, tables created automatically on first start, seeded from the MySQL import tables).
- **Viva talking points:** the template method `charge()` in `AbstractPaymentProcessor`; how file parsing and error collection work; how the report is generated.

---

## Individual contribution evidence

For the marks on individual contribution and commit history:

1. Each member keeps their commits on their own GitHub account (README section 7).
2. Each member's component is listed above, so the commit history for that component is easy to match to the member.
3. Each member prepares a short walkthrough of their component for the viva: the classes, the CRUD endpoints, the OOP concepts and one live demo.
