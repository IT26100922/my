# MySQL setup — CineMax movie ticket booking

The project stores all of its data in MySQL. This page is the shortest path from a clean machine to a
running system.

## 1. What you need

| Thing | Version | Notes |
|---|---|---|
| Java | 21 | `java -version` |
| Maven | 3.9 (or use IntelliJ's bundled one) | |
| MySQL | 8.0.x (tested path) | the JDBC driver `mysql-connector-j` is already in `pom.xml`. MariaDB 10.4+ also works. |

## 2. Start the MySQL server

**Windows (MySQL 8 installer)** — it registers a Windows service, usually `MySQL80`:

```cmd
net start MySQL80
```

Check the exact service name with `sc query type= service | findstr /i mysql`. MySQL Workbench can also show
whether the server is running (*Server → Server Status*).

**Linux** — `sudo service mysql start` (MariaDB: `sudo service mariadb start`)
**macOS** — `brew services start mysql`

**Check it answers:**

```bash
mysql -u root -p -e "SELECT VERSION();"
```

It should print `8.0.46`. The project is configured for the login **`root` / `0000`**, so if your root
password is something else either change it (`ALTER USER 'root'@'localhost' IDENTIFIED BY '0000';`) or pass
your own password as `DB_PASS` in step 4.

## 3. Create the database with the script (recommended)

One file does everything — database, optional app user, all 11 tables:

```bash
mysql -u root -p < database/cinemax_mysql_setup.sql
```

MySQL Workbench: *File → Open SQL Script* → run it. It prints `tables_created = 11` at the end.
See `database/README.md` for details.

The app already points at `root` / `0000`, so with that login nothing needs to be set. If you create the
optional `cinemax` user in that script instead, start the app with `DB_USER=cinemax` and
`DB_PASS=Cinemax#2026`.

## 3b. Or just run the app

The script is optional. The app creates everything itself on the first start:

1. The JDBC URL contains `createDatabaseIfNotExist=true`, so the database `movie_booking` is created on the
   first connection.
2. Hibernate (`spring.jpa.hibernate.ddl-auto=update`) creates the fourteen tables.
3. `DataInitializer` copies the rows of the MySQL import tables (`import_users`, `import_movies`,
   `import_showtimes`) into the live tables if those are empty. The sample rows are inserted by
   SECTION 4 of `database/cinemax_mysql_setup.sql`, so run that script once to have them.
4. Colombo and Negombo theatres and screens are seeded in code.

```bash
mvn spring-boot:run          # or the "CineMax Application" run configuration in IntelliJ
```

Open <http://localhost:8080>.

## 4. If your MySQL login is not root / root

Set environment variables instead of editing the properties file:

```bash
DB_HOST=localhost DB_PORT=3306 DB_NAME=movie_booking DB_USER=root DB_PASS=0000 mvn spring-boot:run
```

IntelliJ: *Run → Edit Configurations → Environment variables*.

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | server address |
| `DB_PORT` | `3306` | server port |
| `DB_NAME` | `movie_booking` | database name |
| `DB_USER` | `root` | user |
| `DB_PASS` | `0000` | password |
| `DB_DIALECT` | `org.hibernate.dialect.MySQLDialect` | only needed on MariaDB: `org.hibernate.dialect.MariaDBDialect` |

## 5. Tables you can inspect

| Table | Holds |
|---|---|
| `users` | customers and admins (single table, `user_type` column decides which) |
| `movies` | the catalogue (single table, `movie_format` column: STANDARD / IMAX / THREE_D) |
| `theatres`, `screens` | venues and auditoriums (`screen_type`: STANDARD / PREMIUM / VIP) |
| `showtimes` | a movie on a screen at a time, with the base price |
| `bookings`, `booking_seats` | a reservation and the seat codes it holds |
| `payments` | one row per payment attempt, with fee, method, status and transaction reference |
| `reviews`, `notifications`, `login_attempts` | reviews, customer inbox, login lockout counters |

Useful queries:

```sql
SELECT reference, status, total_amount FROM bookings ORDER BY created_at DESC;
SELECT method, status, amount, convenience_fee FROM payments;
SELECT title, movie_format FROM movies;
SELECT t.name, s.name, s.screen_type, s.row_count, s.seats_per_row FROM screens s JOIN theatres t ON t.id = s.theatre_id;
```

## 6. Reset the data

```bash
mysql -u root -p -e "DROP DATABASE movie_booking;"
mvn spring-boot:run
```

The next start recreates the schema and reloads the seed files.

## 7. No fallback database

MySQL is the only database: H2 is not on the classpath and no other profile exists. If the MySQL server is
down, the app stops at startup with `Communications link failure` instead of silently using another store.

## 8. Common errors

| Message | Cause | Fix |
|---|---|---|
| `Communications link failure` | server not running / wrong host or port | start MySQL, check `DB_PORT` |
| `Access denied for user 'root'@'localhost'` | wrong password | the project uses `root` / `0000`; set `DB_PASS` if your MySQL root password differs |
| `Unknown database 'movie_booking'` | user cannot create databases | `CREATE DATABASE movie_booking CHARACTER SET utf8mb4;` and grant rights |
| `Unable to determine Dialect without JDBC metadata` | wrong dialect for the server | `DB_DIALECT=org.hibernate.dialect.MariaDBDialect` on MariaDB |
| Sinhala/Tamil text shown as `????` | schema created with a non-UTF8 charset | recreate the database with `utf8mb4` |
| `Table 'movie_booking.users' doesn't exist` | schema creation was skipped | check the console for the first `Caused by:` line |

## 9. Tests

`mvn test` uses MySQL too, but a separate database `movie_booking_test` (its three import tables are filled from `src/test/resources/test-seed.sql`)
(`src/test/resources/application-test.properties`) that is created before the first test and dropped after the
last one. Your real `movie_booking` data is never touched. **Start MySQL before running the tests.**
