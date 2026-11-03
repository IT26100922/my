# CineMax – Movie Ticket Booking System

SE1020 Object Oriented Programming project.
A web application for booking cinema seats, built with **Java 17 + Spring Boot 3.3.5**, **MySQL 8 as the only database** and a **modern HTML/CSS/JavaScript** front end with no external dependencies.

It has **two separate login portals** (customer and admin), **full CRUD** on every main entity, **file handling** (copy MySQL import tables into the live tables, write PDF/CSV/text sales reports), and **OOP** throughout (abstraction, inheritance, polymorphism, encapsulation, information hiding).

---

## 1. Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | **17** | The project targets Java 17 (Maven `release 17`, so a newer JDK also works). |
| Maven | 3.9+ | Or use your IDE's Maven support. |
| MySQL | **8.0.x (required)** | The only database: `movie_booking` on `localhost:3306`. Tables and the sample rows in the import tables are created automatically. |

**MySQL must be installed and running** — it is the only database the project uses.

## 2. Setup and run

```bash
# 1. Clone the repository (team members: see section 7 for the git guide)
git clone <repository-url> movie-ticket-booking
cd movie-ticket-booking

# 2. Build (runs the compile step; tests are optional)
mvn clean package -DskipTests

# 3. Run
java -jar target/movie-ticket-booking-1.0.0.jar
# or during development:
mvn spring-boot:run
```

Open **http://localhost:8080** in a browser.

On first start the database `movie_booking` and its tables are created in MySQL. If the live tables are empty, `DataInitializer` copies the sample rows out of the MySQL import tables (users, movies and showtimes) and creates the theatres and screens. On later starts the existing data is kept.

**Nothing else has to be run by hand.** If the import tables are empty too - for example because the
database was created by Hibernate alone and `database/cinemax_mysql_setup.sql` was never loaded -
`SampleImportSeeder` runs the built-in copy of the sample rows (`src/main/resources/seed/sample-import-tables.sql`,
the same five accounts / six films / 36 showtimes as SECTION 4 of the setup script) before anything else, and
logs `Built-in sample rows loaded: 5 accounts, 6 films, 36 showtimes`. That is why the demo logins below
work on a completely empty MySQL server. If you ever see the error `There is no account in the database`, the
seed was refused (usually a read-only database or a dropped table) - run the setup script and restart.

### Demo accounts

| Portal | Email | Password | Role |
|---|---|---|---|
| Admin | admin@cinema.lk | admin123 | SUPER_ADMIN (can manage other admins) |
| Admin | staff@cinema.lk | staff123 | STAFF (cannot manage admins) |
| Admin | door@cinema.lk | door1234 | BOX_OFFICE (may only scan tickets and look bookings up) |
| Customer | customer@cinema.lk | customer123 | Customer |
| Customer | nimali@cinema.lk | nimali123 | Customer |

Customers can also create their own account at **Create account**. The demo data is deliberately large:
`DemoHistorySeeder` writes 30 days of trading (540 past showtimes, 5 000 bookings, 40 customers with the
password `demo1234`), so the reports, the analytics and the paging have something real to work on. It runs
once; switch it off with `cinemax.demo.history=false`.

### Three languages: English, Sinhala and Tamil

The switcher in the top bar (EN / සි / த) changes the whole interface, and the choice is remembered in
`localStorage` under `cinemax-lang`.

**It translates meaning, not spelling.** "Negombo" becomes **මීගමුව / நீர்கொழும்பு**, not a phonetic
copy; "Seat price" becomes **ආසනයක මිල / ஒரு இருக்கையின் விலை**; "no show" becomes **නොපැමිණි අය /
வராதவர்கள்**. The refund rule reads as the rule ("පැය 24කට වැඩි කාලයකට පෙර … සම්පූර්ණ මුදල"), not as the
English sentence with Sinhala letters.

Two tiers do the work:

| Tier | Where | What it covers |
|---|---|---|
| 1. Keys | `data-i18n` attributes + `lang/en.json`, `si.json`, `ta.json` | the fixed chrome: nav, footer, home headings, filter labels |
| 2. Phrases | `lang/phrases-si.json`, `lang/phrases-ta.json` (558 entries each) | every other sentence, label, button, table header, form hint, tooltip and error the site can show |

The phrase tier is keyed by the English sentence itself, so **no page had to be hand-annotated**: `js/i18n.js`
walks the text nodes (and the `placeholder` / `title` / `aria-label` / `alt` attributes), swaps them, and
remembers the English so switching back is lossless. A `MutationObserver` translates what JavaScript
inserts later - admin tables, the seat map, the door scanner, inline toasts - one subtree at a time, so a
paged table with 20 rows costs nothing. Number-bearing wording ("60 of 60 seats left", "130 min · Tamil",
"Sat 31 Oct, 18:45", "CineMax Negombo · Screen 2 - Premium") is handled by pattern rules in the same file,
which re-translate the pieces they capture.

**Sinhala and Tamil fonts ship with the project** (`static/fonts/*.woff2`, Noto Sans Sinhala / Noto Sans
Tamil, SIL OFL - see `fonts/LICENSE-OFL.txt`, declared in `css/fonts.css`). On a Windows or Mac machine with
no Sinhala font installed the page used to draw empty boxes (□□□); now the letters always render, at 400, 600
and 700 weight, with no CDN and no network call.

What deliberately stays in its original language: **data, not interface** - film titles, synopses, cast and
director names, customer names, booking codes and LKR amounts. Translate the database and those follow:
`movie.title` / `movie.description` are free text, so an admin can store a Sinhala or Tamil synopsis and it is
shown as entered.

To change a wording, edit the JSON (or the readable English → Sinhala → Tamil table in `docs/translations/`)
and reload - nothing has to be recompiled. A missing entry simply falls back to English, never to a broken
string. Verification scripts: `python3 /home/user/i18n-work/check-render.py` style sweeps report the leftover
English nodes and the tofu boxes per page; the last run left 0 on every customer page and only film data on
the two pages that show it.

### What the three upgrade tiers added

| Tier | Feature | Where you see it |
|---|---|---|
| 1 | Ticket check-in at the door | `admin-scan.html` — scan or type a code, one clear verdict per scan |
| 1 | Indexes + paging on the big tables | Admin → Bookings / Customers are paged; the indexes are in the setup script |
| 1 | Promo codes (percentage or flat, min spend, validity, use limit) | Admin → Promo codes; the promo box on the booking page |
| 1 | Waitlist ("notify me") for a sold-out show | A sold-out show button on the film page; the customer is told when seats come back |
| 1 | Analytics on the dashboard | Admin → Analytics: revenue per film/screen/day, occupancy by time slot, funnel, no-shows |
| 2 | Seat bands Regular / Premium / VIP | The seat map is coloured by band and the legend shows each price |
| 2 | Wallet with a real ledger | Profile → wallet: top up, pay from it, refunds land back in it at once |
| 2 | English / Sinhala / Tamil | The EN / සි / த switch in the top bar; `lang/*.json` holds the words |
| 2 | `BOX_OFFICE` staff role | Enforced centrally in `config/AuthInterceptor`, not by hiding buttons |
| 2 | Cancel part of a booking | My bookings → "Give back seats"; same refund policy, on the value of those seats |
| 3 | Concurrency test, MockMvc tests | `src/test/java`, see section 3 |
| 3 | OpenAPI / Swagger UI | http://localhost:8080/swagger-ui.html |
| 3 | `@EntityGraph` + batch fetching | No N+1 queries on the booking lists (see `docs/PERFORMANCE.md`) |
| 3 | One-line request log | `HTTP : GET /api/movies 200 12 ms user=-` in the console |
| 3 | GitHub Actions CI, docker-compose | `.github/workflows/ci.yml`, `docker-compose.yml` |
| 3 | Accessibility pass | `docs/ACCESSIBILITY.md`

### API documentation (Swagger UI)

Every REST endpoint is described at **http://localhost:8080/swagger-ui.html** (the raw document is at
`/v3/api-docs`). It is generated from the controllers, so it always matches the code. The API is
session-cookie based: log in through the site first, in the same browser, and "Try it out" will carry
the cookie. Switch it off for a public deployment with `SPRINGDOC_ENABLED=false`.

### Run it with Docker (optional)

```bash
docker compose up --build      # MySQL 8.4 + the app -> http://localhost:8080
docker compose down -v         # and delete the data volumes
```

`docker-compose.yml` loads `database/cinemax_mysql_setup.sql` into the database the first time the volume
is created, then starts the jar built by the `Dockerfile` (Java 17, non-root user, health check on
`/api/movies`). The same schema, no manual step.

### Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request: JDK 17, a MySQL 8.4 service container,
`mvn compile`, then `mvn test`. The test reports are uploaded as an artifact.

### Database setup (MySQL — required)

The project stores everything in a **MySQL database called `movie_booking`**. You need a MySQL server
(MySQL 8.x — MariaDB 10.4+ also works with the same driver).

1. **Start the server.** Windows: `net start MySQL80` (the service the MySQL 8 installer creates).
   Linux: `sudo service mysql start`. macOS: `brew services start mysql`.
   Optionally create the database, the app user and all tables in one go:
   `mysql -u root -p < database/cinemax_mysql_setup.sql` (see `database/README.md`).
   Without it the app creates them itself on the first start.
2. **Nothing else to create by hand.** On the first start the app creates the database `movie_booking`
   (thanks to `createDatabaseIfNotExist=true`), builds all fourteen tables with Hibernate, and copies the
   sample users, movies and showtimes out of the MySQL import tables (`import_movies`, `import_users`,
   `import_showtimes`) into the live tables. Theatres and screens are created in code.
3. **Run the app** (`MovieBookingApplication`) and open http://localhost:8080. Everything you do —
   accounts, bookings, payments, reviews — is now stored in MySQL and survives restarts.

**The default login is `root` / `0000`** and it is already set in `application.properties`, so a fresh
clone starts with no configuration. If your own MySQL login is different, set environment variables before
starting
(in IntelliJ: *Run → Edit Configurations → Environment variables*):

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | server address |
| `DB_PORT` | `3306` | server port |
| `DB_NAME` | `movie_booking` | database name (created automatically) |
| `DB_USER` | `root` | MySQL user |
| `DB_PASS` | `0000` | MySQL password |
| `DB_DIALECT` | `org.hibernate.dialect.MySQLDialect` | only on MariaDB: `org.hibernate.dialect.MariaDBDialect` |

Example — a server whose root password is `secret` instead:

```bash
DB_USER=root DB_PASS=secret mvn spring-boot:run
```

or change the two defaults once, in `src/main/resources/application.properties`:

```properties
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASS:0000}
```

MySQL is the only database in this project: there is no embedded/file database and no H2 on the classpath.

### Inspect the database

Any MySQL client works — MySQL Workbench, DBeaver, or the command line:

```bash
mysql -u root -p movie_booking
mysql> SHOW TABLES;   -- expect 11 tables
mysql> SELECT reference, status, total_amount FROM bookings;
```

The tables are: `users`, `movies`, `theatres`, `screens`, `showtimes`, `bookings`, `booking_seats`,
`payments`, `reviews`, `notifications`, `login_attempts`.

### Reset to the sample data

Drop the database, run the SQL script again (it refills the import tables) and start the app — it rebuilds
the tables and copies the sample rows out of MySQL:

```bash
mysql -u root -p -e "DROP DATABASE movie_booking;"
mysql -u root -p < database/cinemax_mysql_setup.sql
mvn spring-boot:run
```



---

### Troubleshooting startup

- **"Error starting ApplicationContext … Application run failed"**: scroll up in the console to the first line that starts with `Caused by:` or the exception name. That line says what failed.
- **"The MySQL import tables … are empty"**: the app started before `database/cinemax_mysql_setup.sql` was run. Everything still works, the sample rows just are not there. Run SECTION 4 of that script, or fill `import_movies` / `import_users` / `import_showtimes` yourself, then press the Import buttons.
- **IntelliJ**: no working-directory setting is needed for data any more — the sample rows come from MySQL, not from a folder next to `pom.xml`.
- **Page loads without styling (plain text, no colours)**: open http://localhost:8080/css/style.css and http://localhost:8080/css/studio.css. If that is not found, the static files are missing from `target/classes`. Run *Build → Rebuild Project*, or run `mvn clean package`, and make sure `src/main/resources` is marked as a *Resources* folder.
- **Port 8080 already in use**: stop the other application, or start with `--server.port=8081`.
- **Java version**: Java 17 or newer is required (17, 21 and 24 all work). Check with `java -version`.
- **IntelliJ says `JDK isn't specified for module 'movie-ticket-booking'`**: the project has no SDK at all.
  *File -> Project Structure -> Project* -> **SDK** -> pick a JDK 17 or 21 (nothing in the list? *SDKs -> + ->
  Download JDK -> version 21*), apply. Then *Modules -> movie-ticket-booking -> Dependencies* -> set
  **Module SDK = Project SDK**, apply. Full walkthrough of every IDE error: `docs/INTELLIJ_SETUP.md`.
- **IntelliJ says `Cannot compile module ... configured for JVM target 5`**: the IDE module kept an old
  language level. The pom now sets `maven.compiler.source/target/release` to 17, so a re-import fixes it.
  Do this once:
  1. Right-click `pom.xml` → *Maven* → **Reload project** (or the ↻ icon in the Maven tool window).
  2. *File → Project Structure → Project*: **SDK = 17 or 21**, **Language level = 17**.
  3. *File → Project Structure → Modules → movie-ticket-booking → Sources*: **Language level = 17**.
  4. *Settings → Build, Execution, Deployment → Compiler → Java Compiler*: **Project bytecode version = 17**,
     and clear any per-module override that says 5.
  5. *Build → Rebuild Project*, then run `MovieBookingApplication`.
  If your IDE has no JDK 17+ installed, add one first (*Project Structure → SDKs → + → Download JDK → 21*).
  From a terminal the project always builds with Maven regardless of IDE settings: `mvn -q clean package`.
  Every IDE error of this family, start to finish, is listed in `docs/INTELLIJ_SETUP.md`.
- **`APPLICATION FAILED TO START` with a short message instead of a stack trace**: the project ships two
  failure analyzers (`config/diagnostics/`), so the two database problems read like this instead of two
  hundred lines of Java:
  ```
  ***************************
  APPLICATION FAILED TO START
  ***************************

  Description:

  CineMax stopped because it cannot reach the MySQL server.
    Nothing is listening at localhost:3306, so the connection was refused.
    Settings used: DB_HOST=localhost  DB_PORT=3306  DB_USER=root  (password from DB_PASS, default 0000)
  The application is fine - the database is simply not running yet.

  Action:

  1. Start MySQL, then run the project again.
       Windows:  net start MySQL80
  ...
  ```
  The other one says *"MySQL is running, but it refused the login"* and shows where to put `DB_PASS`.
- **"Communications link failure" / "Connection refused"**: the MySQL server is not running, or `DB_HOST`/`DB_PORT` are wrong. Start MySQL (`net start MySQL80`, `sudo service mysql start`) and check the port.
- **"Invalid email or password" for every account**: the `users` table is empty, so no password can match. This is no longer possible on a fresh database (see *Nothing else has to be run by hand* above), but it can happen if somebody deleted every account. Run `database/cinemax_mysql_setup.sql` and restart, or use **Admin -> Files & Reports -> Import users**.
- **"Too many failed login attempts" (HTTP 429)**: five wrong passwords lock the account for 15 minutes. Wait, or clear the counter with `DELETE FROM movie_booking.login_attempts;`.
- **"Access denied for user 'root'@'localhost'"**: MySQL rejected the password. The project ships with `root` / `0000`; if your MySQL root password is different, set `DB_PASS` as an environment variable (IntelliJ: *Run → Edit Configurations → Environment variables*), or change the default on line 19 of `application.properties`. The same password must be used when you run `mysql -u root -p` to load `database/cinemax_mysql_setup.sql`.
- **"Unknown database 'movie_booking'"**: the user is not allowed to create databases. Create it once yourself: `CREATE DATABASE movie_booking CHARACTER SET utf8mb4;` and grant rights to your user.
- **"Unable to determine Dialect without JDBC metadata"**: set `DB_DIALECT=org.hibernate.dialect.MariaDBDialect` when the server is MariaDB (on MySQL 8 the default is already correct).
- **Sinhala/Tamil text stored as `????`**: the database must be `utf8mb4`. The JDBC URL already requests UTF-8; if the schema was created earlier with another charset, recreate the database with `CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`.

## 3. Testing

**Automated tests (JUnit 5).** They also use MySQL, but a separate database `movie_booking_test` that is created and dropped automatically, so your real `movie_booking` data is never touched. Because that schema starts empty, the test run fills the three MySQL import tables from `src/test/resources/test-seed.sql` (the same sample rows the setup script writes) before the application starts, so the tests go through the real import path instead of a text file. Start MySQL before running them:

```bash
mvn test                                   # runs everything under src/test
mvn clean package                          # also runs the tests before building the jar
```

**Important:** `mvn package` runs these tests. If any test fails, the build fails and no jar is produced. To skip them (only when you know why), use `mvn package -DskipTests`.

**Test output:** `mvn -q` (quiet mode) hides the test results. Use plain `mvn test` or `mvn package` to see each test name and its result. Failures show their details in the console and in `target/surefire-reports/`.

| Test class | What it checks |
|---|---|
| `LoginSecurityTest` | Per-email lockout after 5 wrong passwords (429), per-IP limit after 20 failures from one address (429) without locking other customers, admin and staff levels, STAFF cannot list admins, admin data needs a session, logout expires the cookie, security headers, 8-character minimum password |
| `BookingFlowTest` | Register, book a seat, pay, confirmation notification, a second customer cannot take the same seat or read the booking |
| `BookingLifecycleTest` | Refunds: full at 3 days, 50% at 12 hours, none at 1 hour. Seat hold expiry frees the seat, notifies the customer and leaves paid bookings alone. Reminder sweep sends one reminder per booking (even if run twice) and writes it to the email outbox. Seeded showtimes are bookable on a fresh database |
| `PricingRulesTest` | Which price rule applies (weekday, weekend, matinee), matinee wins over weekend, the 12:00 boundary, and the price for each rule |
| `CancellationPolicyTest` | Refund rule boundaries (24 h, 2 h), 50% rounding (half up), and no refund under 2 hours |
| `SeedDayShiftTest` | Seed showtimes that are in the past are moved forward by whole days, and the smallest move that works is used |
| `SeatConcurrencyTest` | Two threads book the same seat at the same moment: exactly one wins, and the seat is held once (the pessimistic lock on the showtime row) |
| `PartialCancelTest` | Giving back one seat of a paid booking refunds that seat at the 24 h rate, keeps the other seats, frees the released seat and refuses to drop the last one |
| `PromoAndWalletTest` | Admin creates a promo, the customer gets the discount on the booking; a wallet top-up, a wallet payment and a refund each write their own ledger row and the balance adds up; a customer with wallet history can still delete their account |
| `CheckInTest` | A ticket scans once (OK, then ALREADY_USED), an unpaid hold, a cancelled booking, an unknown code and a ticket for another day each get their own verdict; today's attendance adds up |
| `WaitlistTest` | A full screen takes a waitlist entry, and freeing seats notifies the waiting customer once |
| `RoleGuardTest` | Anonymous → 401, customer on the admin API → 403, admin on the customer API → 403, and BOX_OFFICE may scan tickets but not read customers, promos, reports or analytics |
| `AnalyticsApiTest` | The analytics endpoint returns the KPI block, the funnel, revenue per film/screen and the payment split |

**Live checks** (with the app running on port 8080):

```bash
python3 scripts/smoke-test.py          # API tests: 91 checks (auth, CRUD, pricing, seat rules, refunds, files,
                                       #   promo codes, wallet, seat bands, part-cancel, waitlist, check-in,
                                       #   analytics, paging, the BOX_OFFICE fence, the language files, Swagger)
python3 scripts/upgrade-api-check.py   # API tests: 47 checks, the upgrade on its own
```

Browser test (needs `pip install playwright && python3 -m playwright install chromium`):

```bash
python3 scripts/ui-check.py              # UI tests: registration, booking, payment, admin CRUD, route guards
python3 scripts/upgrade-browser-sweep.py # every page once + the upgrade screens (32 checks, no JS errors)
```

The smoke test deletes only the data it creates. It never deletes the sample accounts. The UI check creates a new customer account and one booking on every run. Screenshots are written to `ui-shots/`, which is git-ignored.

---

## 4. Project structure

```
movie-ticket-booking/
├── pom.xml
├── README.md
├── database/          # cinemax_mysql_setup.sql - one-shot MySQL setup
│                      # (database + user + 11 tables + 3 MySQL import tables and their sample rows)
├── .gitignore
├── Dockerfile          # jar in a Java 17 image, run as an ordinary user
├── docker-compose.yml  # MySQL 8.4 + the application, one command
├── .github/workflows/ci.yml   # GitHub Actions: mvn test against a MySQL service container
├── data/
│   ├── reports/       # timestamped text sales reports (written by FileService)
│   └── outbox/        # simulated notification emails (written by NotificationService)
├── docs/
│   ├── CLASS_DIAGRAM.md        # Mermaid class diagrams
│   ├── CRUD_BY_MEMBER.md       # which screens/endpoints each member owns
│   ├── INTELLIJ_SETUP.md       # fixing "JDK isn't specified" / "JVM target 5"
│   ├── MYSQL_SETUP.md          # database install + connection
│   ├── TEAM_WORKLOAD.md        # 6-member division of work
│   ├── PERFORMANCE.md          # indexes, paging and the N+1 audit
│   ├── ACCESSIBILITY.md        # the accessibility / Lighthouse pass
│   └── UPGRADE_ROADMAP.md      # what to build next, costed against this code
├── scripts/
│   ├── smoke-test.py           # API end-to-end test
│   └── ui-check.py             # browser end-to-end test
└── src/
    ├── main/
    │   ├── java/com/moviebooking/
    │   │   ├── MovieBookingApplication.java
    │   │   ├── user/        entity · dto · repository · service · controller · service/auth
    │   │   ├── movie/       entity · dto · repository · service · controller
    │   │   ├── theatre/     entity · dto · repository · service · controller   (Theatre, Screen)
    │   │   ├── show/        entity · dto · repository · service · controller · pricing
    │   │   ├── booking/     entity · dto · repository · service · controller · policy
    │   │   ├── payment/     entity · dto · repository · service · controller · processor
    │   │   ├── review/      entity · dto · repository · service · controller
    │   │   ├── ticket/      entity · dto · repository · service · controller   (e-ticket / QR)
    │   │   ├── file/        entity · dto · repository · service · controller   (MySQL imports, reports)
    │   │   ├── notification/ entity · repository · service · controller
    │   │   ├── report/      service · controller
    │   │   ├── config/      DataInitializer, AuthInterceptor, WebConfig, SessionKeys
    │   │   └── exception/   GlobalExceptionHandler, BusinessException, ResourceNotFoundException
    │   └── resources/
    │       ├── application.properties   # MySQL connection
    │       └── static/                  # HTML/CSS/JS front end
    │           ├── index.html · movie.html · booking.html · my-bookings.html · profile.html · theatres.html
    │           ├── customer-login.html · customer-register.html · admin-login.html · admin-dashboard.html
    │           ├── live.html            # live database view
    │           ├── css/style.css          # base layout, components, tokens
    │           ├── css/studio.css          # theme layer: depth, glass, motion, home sections
    │           ├── js/common.js   # API helpers, nav, theme, toasts, the themed dropdown
    │           └── js/admin.js
    └── test/
        ├── java/com/moviebooking/       # JUnit tests, MySQL database movie_booking_test
        │   ├── user/service/auth/LoginSecurityTest.java
        │   ├── booking/BookingFlowTest.java
        │   └── file/service/StagingShowtimeShiftTest.java
        └── resources/
            ├── application-test.properties   # points the tests at movie_booking_test
            └── test-seed.sql                 # sample rows for the three import tables
```

Every module follows the same layers: **entity** (JPA model) → **repository** (Spring Data) → **service** (business rules) → **controller** (REST endpoints), with **dto** records as the only objects that cross the HTTP boundary.

---

## 5. CRUD map

| Entity | Create | Read | Update | Delete | Where |
|---|---|---|---|---|---|
| Customer | `POST /api/auth/customer/register` | `GET /api/customer/profile`; admin `GET /api/admin/customers` (search `q`), `GET /api/admin/customers/page?page=&size=` | `PUT /api/customer/profile` | `DELETE /api/customer/profile`; admin `DELETE /api/admin/customers/{id}` (blocked if booking history) | Member 1 |
| Admin | `POST /api/admin/admins` (SUPER_ADMIN) | `GET /api/admin/admins` | `PUT /api/admin/admins/{id}` (SUPER_ADMIN) | `DELETE /api/admin/admins/{id}` (SUPER_ADMIN) | Member 1 |
| Movie | `POST /api/admin/movies` | `GET /api/movies` (filters `title`, `genre`), `GET /api/movies/{id}` | `PUT /api/admin/movies/{id}` (format cannot change) | `DELETE /api/admin/movies/{id}` (blocked if showtimes exist) | Member 2 |
| Theatre | `POST /api/admin/theatres` | `GET /api/theatres`, `GET /api/theatres/{id}` | `PUT /api/admin/theatres/{id}` | `DELETE /api/admin/theatres/{id}` (blocked if screens exist) | Member 3 |
| Screen | `POST /api/admin/screens` | `GET /api/screens?theatreId=`, `GET /api/screens/{id}` | `PUT /api/admin/screens/{id}` (type cannot change; layout locked when showtimes exist) | `DELETE /api/admin/screens/{id}` (blocked if showtimes exist) | Member 3 |
| Showtime | `POST /api/admin/shows` | `GET /api/shows?movieId=&date=`, `GET /api/shows/{id}`, admin `GET /api/admin/shows` | `PUT /api/admin/shows/{id}` | `DELETE /api/admin/shows/{id}` (blocked if bookings exist) | Member 4 |
| Booking | `POST /api/customer/bookings` (optional `promoCode`) | `GET /api/customer/bookings`, `GET /api/customer/bookings/{id}`; admin `GET /api/admin/bookings`, `GET /api/admin/bookings/page?page=&size=` | `PUT /api/customer/bookings/{id}` (seats only while PENDING_PAYMENT) | Customer `DELETE /api/customer/bookings/{id}` (pending: removed; confirmed: refund policy) or `POST /api/customer/bookings/{id}/cancel-seats` (give back some seats). Admin `DELETE /api/admin/bookings/{id}` (CANCELLED only) | Member 5 |
| Payment | `POST /api/customer/payments` | `GET /api/customer/payments`; admin `GET /api/admin/payments` | Admin `PUT /api/admin/payments/{id}` (manual refund) | Admin `DELETE /api/admin/payments/{id}` (REFUNDED only) | Member 6 |
| Import | `POST /api/admin/files/import-movies` (from `import_movies`), `POST /api/admin/files/import-users` (from `import_users`), `POST /api/admin/files/import-showtimes` (from `import_showtimes`) | `GET /api/admin/files/status` (staging and live row counts) | `POST /api/admin/files/export-movies`, `POST /api/admin/files/export-showtimes` (copy a live table back into its import table) | – | Member 6 |
| Promo code | `POST /api/admin/promos` | `GET /api/admin/promos`; customer `POST /api/customer/promo/check` | `PUT /api/admin/promos/{id}` | `DELETE /api/admin/promos/{id}` | Upgrade |
| Wallet | `POST /api/customer/wallet/top-up` | `GET /api/customer/wallet` (balance and the ledger) | – (every row is written by a top-up, a payment or a refund) | – | Upgrade |
| Waitlist | `POST /api/customer/waitlist/{showId}` | `GET /api/customer/waitlist` | – | `DELETE /api/customer/waitlist/{showId}` | Upgrade |
| Check-in | `POST /api/admin/tickets/validate` (`{"code": "BK..."}`) | `GET /api/admin/tickets/today` | – | – | Upgrade |
| Analytics | – | `GET /api/admin/analytics?days=7` | – | – | Upgrade |
| Reports | – | `GET /api/admin/reports/sales` (JSON), `GET /api/admin/reports/sales.pdf`, `GET /api/admin/reports/sales.csv`; `GET /api/admin/files/export-sales` (timestamped text report in `data/reports/`) | – | – | Member 6 |

### Reports (PDF and CSV)

The admin **Files & Reports** page has four cards: *Import movies*, *Import users*, *Import showtimes* (each writes into MySQL and reports `imported` / `skipped` counts) and *Sales report*.

| Button | Endpoint | What you get |
|---|---|---|
| **Sales report (PDF)** | `GET /api/admin/reports/sales.pdf` | Two-page designed report: KPI boxes, daily-revenue bar chart, payment-method donut, top-five-movies bars, then all figures as tables |
| **Sales report (CSV)** | `GET /api/admin/reports/sales.csv` | One file with `SUMMARY`, `DAILY`, `BY_MOVIE` and `BY_PAYMENT_METHOD` sections; every row carries an in-cell bar chart, and one summary cell holds a sparkline of the whole period |

Both accept optional `?from=YYYY-MM-DD&to=YYYY-MM-DD` (default: the last 14 days).

The PDF has the edge when it is printed or handed in; the CSV has the edge when someone wants to work with the numbers. CSV cannot hold an image, so the graphics are drawn with block characters in a `bar` column (28–34 cells wide, eighth-of-a-cell steps) plus a one-cell sparkline `daily_net_sparkline`. Set the `bar` column to Consolas or Courier New in Excel and the bars line up; the file also starts with a UTF-8 byte-order mark so Excel on Windows shows the blocks correctly. The numeric columns stay clean (`50216.80`, no thousands separator) so formulas still work.

Want louder bars without leaving Excel? Select the `net_revenue_lkr` column and use **Home → Conditional Formatting → Data Bars**; Excel paints native coloured bars over the numbers, and the block-character `bar` column keeps working as a plain-text fallback for anything that cannot do that (email, git diff, `cat`).

`docs/samples/` holds one example of each file, produced from temporary demo bookings, so you can see the layout without entering data first. Money figures in every format exclude payments on cancelled bookings.

### Imports (MySQL only)

The three import buttons never touch the disk. They copy rows from three ordinary MySQL tables into the live tables:

| Button | Reads (MySQL) | Writes | Skips |
|---|---|---|---|
| **Import movies** | `import_movies` | `movies` | a title that already exists |
| **Import users** | `import_users` (BCrypt hashes only) | `users` | an email that already exists |
| **Import showtimes** | `import_showtimes` | `showtimes` | a past show, or a screening that is already booked on that screen at that time |

To import your own data, insert rows with plain SQL (Workbench, the `mysql` CLI, or another program) and press the button:

```sql
INSERT INTO import_movies (title, genre, language, duration_minutes, rating, movie_format,
                           release_date, director, cast_members, description)
VALUES ('Blue Harbour', 'Drama', 'Sinhala', 118, 'PG-13', 'STANDARD',
        '2026-12-05', 'Nadeesha Perera', 'Kasun Silva, Amali Fernando', 'A harbour town keeps one secret too many.');

-- then: Admin -> Files & Reports -> Import movies
```

`GET /api/admin/files/status` returns the row counts of all three import tables and their live counterparts, which is what the cards show under each button. The two *refresh* actions run the other way — `POST /api/admin/files/export-movies` and `/export-showtimes` copy a live table back into its import table, so you always have a current snapshot to work from.

SECTION 4 of `database/cinemax_mysql_setup.sql` creates the three tables and inserts the sample rows (6 movies, 4 accounts, 36 screenings dated from tomorrow), so a fresh install has something to import.

**Admin forms:** when you edit a movie, its format is locked. When you edit a screen, its type is locked. The form shows the reason under the field, because the server refuses those changes. Scene image URLs are kept when you save a movie without touching them. Admin rows are shown only to SUPER_ADMIN accounts.

Error responses share one shape: `{ "message": "...", "timestamp": "..." }`. `404` for missing records, `400` for validation and business-rule violations, `409` for database constraint conflicts, `401`/`403` for authentication and role failures.

### Business rules (summary)

- **Seats:** rows are letters (max 26), seats per row are numbers (max 30). A booking holds at most 10 seats. A seat held by a PENDING_PAYMENT, CONFIRMED or CHECKED_IN booking cannot be taken again.
- **Seat bands:** the rows of a screen are split into three bands by position — the front third is **Regular** (×1.00), the middle **Premium** (×1.20) and the back third **VIP** (×1.45). The band price is added on top of the usual pricing rule, so a VIP seat at a matinee is cheaper than a VIP seat on a Saturday night.
- **Promo codes:** `PERCENT` or `FLAT`, with an optional minimum spend, a validity window and a maximum number of uses. The discount is taken off the seat value only (never the convenience fee), is stored on the booking (`promo_code`, `discount_amount`) and is counted once per booking. A refund is worked out on what the customer actually paid, so a discounted seat refunds the discounted amount.
- **Wallet:** a real ledger (`wallet_transactions`: TOP_UP, PAYMENT, REFUND, each with the balance after the row). A wallet payment is refused when the balance is too low, and a refund is credited back at once - it never waits for the bank. Refund percentages (24 h 100 %, 2-24 h 50 %, under 2 h 0 %) are unchanged.
- **Part cancellation:** a paid booking can give back some of its seats. The refund uses the same policy, on the value of the released seats only; the rest of the booking stays CONFIRMED. The last seat cannot be dropped that way - that is a full cancellation.
- **Check-in:** a ticket is scanned once. The verdict is one of `OK`, `ALREADY_USED`, `CANCELLED`, `NOT_PAID`, `NOT_FOUND`, `WRONG_DAY`, `NOT_STARTED_TODAY`. A successful scan sets the booking to CHECKED_IN with the time and the staff member.
- **Price:** `base price × screen multiplier + movie surcharge`, then the highest-priority pricing rule applies: matinee (before 12:00, ×0.80), weekend (Sat/Sun, ×1.20), otherwise weekday (×1.00).
- **Screen multipliers:** Standard ×1.00, Premium ×1.30, VIP ×1.80. **Movie surcharges:** Standard 0, 3D +200, IMAX +300 (LKR).
- **Cancellation refunds (confirmed bookings):** 24 h or more before the show 100%; 2–24 h 50%; under 2 h 0%.
- **Payments:** card 16 digits with a 2% fee (only the last 4 digits are stored); wallet ID 5–40 characters, no fee; cash counter code `CNT-NN`, no fee. A successful payment confirms the booking and adds loyalty points equal to the number of seats.
- **Authentication:** session-based. Customers log in at `/api/auth/customer/login`, admins at `/api/auth/admin/login`. `AuthInterceptor` checks the role for `/api/customer/**` and `/api/admin/**`. Passwords are stored as BCrypt hashes and must be at least 8 characters. Sessions end after 30 minutes idle.
- **Login lockout:** two limits, both kept in the `login_attempts` table with SHA-256 keys (so no email or address is stored in readable form), and both kept across restarts:
  - **Per email:** 5 wrong passwords for one email within 15 minutes lock that email for 15 minutes (HTTP 429). A successful log-in clears this counter.
  - **Per IP address:** 20 wrong passwords from one address within 15 minutes lock that address for 15 minutes (HTTP 429). This stops one machine from locking out many customers. It is higher than the email limit, because several people on one network can share an address.
  - Unknown emails are counted too, so the lock does not reveal which emails have accounts.
- **Seat hold:** a booking that is not yet paid holds its seats for 10 minutes (`app.booking.hold-minutes`), then the seats are released and the customer is notified.
- **Show reminders:** a background job runs every minute. A confirmed booking gets one in-app reminder about 24 hours before the show and one about 2 hours before. They appear in the customer bell icon inbox, and each one is also written as a simulated email (see Notification emails below).
- **Notification emails (simulated):** booking confirmed, booking cancelled, show reminders and expired seat holds are each appended to `data/outbox/outbox.txt`, with the recipient, subject and message. No mail server is contacted. Open that file to see what a customer would have received. The folder is git-ignored. Change it with `app.email.outbox-dir`.
- **Showtime dates in the import table:** `import_showtimes` stores fixed dates, so on an old database they can all be in the past. The import then moves every staged show forward by the same whole number of days, so the timetable keeps its shape and every show can be booked. Live showtimes are never changed by that rule. SECTION 4 of the SQL script writes the sample dates relative to `CURDATE()`, so a fresh install never needs the shift.

---

## 5.4 The interface

The front end is two plain stylesheets and no libraries, so the pages work offline and inside a
preview iframe:

| File | What it does |
|------|--------------|
| `css/style.css` | Layout skeleton, component classes, dark/light tokens, responsive rules |
| `css/studio.css` | Loaded after it: the visual layer — glass panels with gradient hairlines, the 3D hero stage, editorial type, hand-drawn accents, the home-page ticker and closing band |

Both files share one token block (`--accent`, `--card`, `--radius`, `--shadow`, `--grain`, …). Change a
token in `studio.css` and the whole site follows, in both themes. Everything decorative is CSS or an
inline SVG data URI — there are no image files to lose and no CDN to reach.

* **Two themes** — dark by default, light via the toggle; the choice is stored in `localStorage`
  (`cinemax-theme`) and read before first paint, so there is no flash.
* **Dropdowns wear the theme too** — a native `<select>` popup is painted by the operating system, so on
  the home filter bar `js/common.js` keeps the `<select>` (hidden, still the source of truth for `value`
  and `change`) and draws a themed listbox in its place: glass chip trigger, coral tick on the current
  choice, arrow/Home/End/Enter/Escape keys, first-letter jump. Pages keep using `select.value`; to use it
  elsewhere add `data-cm-select` to any `<select>`. A filter that is narrowing the list gets a coral chip,
  so the bar always shows which dropdowns are doing something.
* **Depth, not decoration on top of content** — cards, tables, modals and the sidebar are one glass
  material with a 1px light rim and a tinted shadow, so new pages look like the old ones.
* **The hero is a stage** — the featured poster floats on a tilted 3D card inside a dashed frame. Above it
  sits the live-seat chip, below it the "from" price tag, then the perspective floor and the counter rail
  (`01 / 05`) — three stacked pieces with real gaps, so nothing is ever laid across the artwork or spills
  past the frame, on any window height. Left/right arrow keys move through the films as well as the arrows.
* **Motion is small and honest** — 150–300 ms lifts and presses only, plus a slow ticker strip.
  `prefers-reduced-motion` removes the transforms and the ticker animation.

---

## 6. OOP map

| OOP concept | Where it is used |
|---|---|
| **Abstraction** | Abstract `User`, `Movie`, `Screen`; interfaces `AuthStrategy`, `PricingStrategy`, `CancellationPolicy`, `PaymentProcessor`. Services depend on these types, not concrete classes. |
| **Inheritance** | `Customer` and `Admin` extend `User`. `StandardMovie`, `ImaxMovie`, `ThreeDMovie` extend `Movie`. `StandardScreen`, `PremiumScreen`, `VipScreen` extend `Screen`. `CustomerAuthStrategy` and `AdminAuthStrategy` extend `AbstractAuthStrategy`. `CardPaymentProcessor`, `WalletPaymentProcessor`, `CashCounterPaymentProcessor` extend `AbstractPaymentProcessor`. |
| **Polymorphism** | `movie.getSurcharge()` and `screen.getPriceMultiplier()` resolve to the subclass. `PricingStrategy` picks the matching rule by priority. JPA single-table inheritance loads the correct subclass from the database. |
| **Encapsulation** | Entity fields are private with getters and setters. Business rules sit in services, not in controllers. |
| **Information hiding** | Response DTOs (`*Response` records) hide the password hash and internal entity structure. Payment cards store only the last four digits. Service classes expose only the operations other layers need. |
| **Design patterns** | *Factory*: `MovieFactory`, `ScreenFactory`. *Strategy*: auth, pricing, cancellation policies, payment processors. *Template method*: `AbstractAuthStrategy`, `AbstractPaymentProcessor`. |

Class diagrams are in [docs/CLASS_DIAGRAM.md](docs/CLASS_DIAGRAM.md).

---

## 7. Git guide for the team

The marking guide awards marks for individual contribution shown in the **GitHub commit history**, so every member should commit their own work from their own account.

1. **Each member clones the repository and works on a feature branch:**
   ```bash
   git checkout -b feature/<component-name>      # e.g. feature/movie-catalogue
   ```
2. **Commit small, meaningful changes with clear messages**, for example `Add movie format validation` or `Implement seat-code parser`. Avoid one giant commit at the end.
3. **Push your branch and open a pull request** into `main`. A second team member reviews it. The review shows up in the history as well.
4. **Keep `main` working.** Before merging, run the app and `python3 scripts/smoke-test.py`.
5. **Do not commit generated files.** `target/`, `data/*.db`, `data/reports/` and `ui-shots/` are already in `.gitignore`.
6. **Do not commit on someone else's behalf.** Use `git config user.name` and `git config user.email` with your own details.
7. **Pull often** (`git pull --rebase origin main`) to avoid large merge conflicts in shared files such as `DataInitializer.java` and `style.css`.

Suggested branch names follow the six components in [docs/TEAM_WORKLOAD.md](docs/TEAM_WORKLOAD.md).

---

## 8. Notes and limitations

- The payment processors are **simulated**. No real payment gateway is contacted.
- The app stores its data in MySQL (`movie_booking`); there is no other database option. The automated tests use a separate `movie_booking_test` database on the same server.
- Seat selection is "first come, first served". Unpaid seats are held for 10 minutes and then released (see Business rules).
- Sessions are stored in server memory. Restarting the app logs everyone out.
- **Cookie security:** the session cookie is HttpOnly and SameSite=Lax. The `Secure` flag is off by default so local `http://` demos work. Set `SESSION_COOKIE_SECURE=true` when the site is served over HTTPS.
- **CSRF:** there are no CSRF tokens on admin forms. SameSite=Lax blocks most cross-site form posts in current browsers, but this is not a full CSRF defence.
- **Admin list:** only SUPER_ADMIN accounts can read the admin list (`GET /api/admin/admins`).
- Notification emails are **simulated**: they go to `data/outbox/outbox.txt`, not to a real inbox. Swap the `EmailSender` class to send real mail.
- Admins can edit only the permission level. Name and email cannot be changed from the admin page yet.
- An admin can change the time, screen or price of a showtime that already has bookings. The app does not block this, so tell customers when you change a booked show.
- The per-IP limit uses the connection address. If the app is behind a reverse proxy, every user may appear to come from the proxy's address, so proxy setup must be reviewed before going live.
