# database/

One file: **`cinemax_mysql_setup.sql`** — everything the project needs in MySQL, in one run.

## What it does

| Section | Creates |
|---|---|
| 1 | the database `movie_booking` (utf8mb4) |
| 2 | the application user `cinemax` with rights on that database only *(optional)* |
| 3 | the 11 tables, unique keys and foreign keys — the exact schema Hibernate expects |
| 4 | a short report so you can see it worked |

Every statement uses `IF NOT EXISTS`, so running it twice is harmless. The column and
constraint names match Hibernate's own names, which means `spring.jpa.hibernate.ddl-auto=update`
finds nothing to change afterwards (verified: the schema is byte-identical before and after the
app starts).

## Run it once

```cmd
mysql -u root -p < database\cinemax_mysql_setup.sql
```

(on Linux/macOS the same line with a forward slash). This is the root password you chose while installing
MySQL 8.

MySQL Workbench: *File → Open SQL Script* → choose the file → click the lightning bolt.
phpMyAdmin: *Import* → choose the file → *Go*.

## Then start the app

```bash
mvn spring-boot:run
```

The live tables are empty on purpose: SECTION 4 of the script fills the three import tables
(`import_movies`, `import_users`, `import_showtimes`), and on the first start the app copies them into
`movies`, `users` and `showtimes` (4 users, 6 movies, 2 theatres, 6 screens, 36 showtimes). You can run
that import again at any time from Admin -> Files & Reports.

The app connects as `root` / `0000` by default, so with that login just press Run — no environment
variables are needed. If you created the optional `cinemax` user instead, set:

```
DB_USER=cinemax
DB_PASS=Cinemax#2026
```

IntelliJ: *Run → Edit Configurations → CineMax Application → Environment variables*.

## Start over

```sql
DROP DATABASE movie_booking;
```

Run the script again, then start the app — the schema and the seed data are rebuilt.
