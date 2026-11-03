-- =====================================================================================
--  CineMax Movie Ticket Booking  --  one-shot MySQL 8 setup script
--  Tested against MySQL 8.0 syntax (and MariaDB 11 with the collation swapped).
--
--  WHAT THIS SCRIPT DOES
--    1. Creates the database  movie_booking  (utf8mb4, so Sinhala/Tamil text is safe).
--    2. Creates the application user  cinemax  with a password and grants it rights
--       on this one database only.                                        [optional]
--    3. Creates the 11 application tables, keys and foreign keys the project uses.
--    4. Creates the 3 MySQL import tables (import_movies, import_users, import_showtimes)
--       and fills them with the sample rows the Import buttons copy into the app.
--    5. Prints a summary so you can see that it worked.
--
--  HOW TO RUN IT  (as the MySQL root user, once)
--    Windows / macOS / Linux terminal:
--        mysql -u root -p < cinemax_mysql_setup.sql
--    MySQL Workbench:
--        File -> Open SQL Script -> choose this file -> press the lightning bolt
--    phpMyAdmin (if you ever use it):
--        Import -> choose this file -> Go
--
--  IMPORTANT
--    * The app can also build these tables by itself (spring.jpa.hibernate.ddl-auto=update).
--      Running this script first is safe: every statement uses IF NOT EXISTS, and the
--      column/constraint names are exactly the ones Hibernate expects, so it will not
--      try to change anything afterwards.
--    * Change the password in SECTION 2 before you run it, or skip SECTION 2 and keep
--      using root. If you create the cinemax user, start the app with
--          DB_USER=cinemax   DB_PASS=Cinemax#2026
--    * The sample movies, users and showtimes live in the import tables (SECTION 4), never in a
--      text file. The app copies them into the live tables on its first start, and the buttons in
--      Admin -> Files & Reports copy them again whenever you want. Theatres and screens are created
--      by the app itself, because they describe the cinema layout rather than import data.
--
--  After running:  mvn spring-boot:run      ->  http://localhost:8080
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- SECTION 1 -- the database
-- -------------------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `movie_booking`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;          -- MySQL 8 default; on MariaDB use utf8mb4_general_ci

USE `movie_booking`;

-- -------------------------------------------------------------------------------------
-- SECTION 2 -- the application user (optional but recommended)
--   Using a dedicated user instead of root means a mistake in the app can only damage
--   one database. Change the password here AND in the app's DB_PASS variable.
--   Not needed: the app is already configured for root / 0000, which is the simplest option.
-- -------------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'cinemax'@'localhost'
    IDENTIFIED WITH caching_sha2_password BY 'Cinemax#2026';
CREATE USER IF NOT EXISTS 'cinemax'@'127.0.0.1'
    IDENTIFIED WITH caching_sha2_password BY 'Cinemax#2026';

GRANT ALL PRIVILEGES ON `movie_booking`.* TO 'cinemax'@'localhost';
GRANT ALL PRIVILEGES ON `movie_booking`.* TO 'cinemax'@'127.0.0.1';
FLUSH PRIVILEGES;

-- -------------------------------------------------------------------------------------
-- SECTION 3 -- tables
--   Order matters: a table is created after the tables it points at.
--   The first column of movies / screens / users is the JPA discriminator column --
--   that is how inheritance is stored in one table per family (SINGLE_TABLE).
-- -------------------------------------------------------------------------------------

-- Member 1: customers and admins share one table; user_type says which subclass it is.
CREATE TABLE IF NOT EXISTS `users` (
  `user_type`        varchar(31)  NOT NULL,               -- CUSTOMER | ADMIN
  `id`               bigint       NOT NULL AUTO_INCREMENT,
  `created_at`       datetime(6)  NOT NULL,
  `email`            varchar(150) NOT NULL,
  `full_name`        varchar(100) NOT NULL,
  `password_hash`    varchar(255) NOT NULL,               -- BCrypt hash, never a raw password
  `phone`            varchar(20)  DEFAULT NULL,
  `permission_level` varchar(30)  DEFAULT NULL,           -- SUPER_ADMIN | STAFF | BOX_OFFICE (admins only)
  `loyalty_points`   int          DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 3: the cinema venues.
CREATE TABLE IF NOT EXISTS `theatres` (
  `id`      bigint       NOT NULL AUTO_INCREMENT,
  `address` varchar(200) DEFAULT NULL,
  `city`    varchar(60)  NOT NULL,
  `name`    varchar(100) NOT NULL,
  `phone`   varchar(20)  DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 2: the catalogue. movie_format = STANDARD | IMAX | THREE_D decides the subclass
-- that is rebuilt when a row is read.
CREATE TABLE IF NOT EXISTS `movies` (
  `movie_format`     varchar(31)   NOT NULL,
  `id`               bigint        NOT NULL AUTO_INCREMENT,
  `cast_members`     varchar(500)  DEFAULT NULL,
  `description`      varchar(1000) DEFAULT NULL,
  `director`         varchar(150)  DEFAULT NULL,
  `duration_minutes` int           NOT NULL,
  `genre`            varchar(60)   NOT NULL,
  `language`         varchar(40)   NOT NULL,
  `poster_url`       varchar(500)  DEFAULT NULL,
  `producers`        varchar(300)  DEFAULT NULL,
  `rating`           varchar(10)   DEFAULT NULL,
  `release_date`     date          DEFAULT NULL,
  `still_urls`       varchar(2000) DEFAULT NULL,
  `title`            varchar(150)  NOT NULL,
  `trailer_url`      varchar(500)  DEFAULT NULL,
  `writers`          varchar(300)  DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 3: auditoriums inside a theatre. screen_type = STANDARD | PREMIUM | VIP decides
-- the price multiplier (1.00 / 1.30 / 1.80).
CREATE TABLE IF NOT EXISTS `screens` (
  `screen_type`   varchar(31) NOT NULL,
  `id`            bigint      NOT NULL AUTO_INCREMENT,
  `name`          varchar(60) NOT NULL,
  `row_count`     int         NOT NULL,
  `seats_per_row` int         NOT NULL,
  `theatre_id`    bigint      NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKomykyk08ts7cl7rwxp5qlaear` (`theatre_id`),
  CONSTRAINT `FKomykyk08ts7cl7rwxp5qlaear`
    FOREIGN KEY (`theatre_id`) REFERENCES `theatres` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 4: a movie on a screen at a time. The final seat price is calculated from
-- base_price + the screen multiplier + the movie surcharge, then the pricing strategy.
CREATE TABLE IF NOT EXISTS `showtimes` (
  `id`         bigint        NOT NULL AUTO_INCREMENT,
  `base_price` decimal(10,2) NOT NULL,
  `end_time`   datetime(6)   NOT NULL,
  `start_time` datetime(6)   NOT NULL,
  `movie_id`   bigint        NOT NULL,
  `screen_id`  bigint        NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeltpyuei1d5g3n6ikpsjwwil6` (`movie_id`),
  KEY `FKqh5i17921jatisuwyu12ho500` (`screen_id`),
  CONSTRAINT `FKeltpyuei1d5g3n6ikpsjwwil6`
    FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`),
  CONSTRAINT `FKqh5i17921jatisuwyu12ho500`
    FOREIGN KEY (`screen_id`) REFERENCES `screens` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 5: a reservation. reference is the customer-facing code (BK + 8 hex characters);
-- status decides whether the seats are held, sold or released.
CREATE TABLE IF NOT EXISTS `bookings` (
  `id`           bigint        NOT NULL AUTO_INCREMENT,
  `created_at`   datetime(6)   NOT NULL,
  `expires_at`   datetime(6)   DEFAULT NULL,     -- end of the 10-minute seat hold
  `reference`    varchar(20)   NOT NULL,
  `status`       enum('CANCELLED','CONFIRMED','PENDING_PAYMENT') NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `customer_id`  bigint        NOT NULL,
  `show_id`      bigint        NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpki3ddx1043pwavq8jibqcxkw` (`reference`),
  KEY `FKib6gjgj2e9binkktxmm175bmm` (`customer_id`),
  KEY `FKdsuyaskf7mjf80cr4pfolbdc6` (`show_id`),
  CONSTRAINT `FKdsuyaskf7mjf80cr4pfolbdc6`
    FOREIGN KEY (`show_id`) REFERENCES `showtimes` (`id`),
  CONSTRAINT `FKib6gjgj2e9binkktxmm175bmm`
    FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 5: the seats of a booking (one row per seat, e.g. A1).
CREATE TABLE IF NOT EXISTS `booking_seats` (
  `booking_id` bigint      NOT NULL,
  `seat_code`  varchar(6)  NOT NULL,
  PRIMARY KEY (`booking_id`,`seat_code`),
  CONSTRAINT `FKmbi9ciapn0nvat63t0a8tv478`
    FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 6: money. One row per payment or refund: method decides the processor, status
-- decides whether it can be refunded or deleted.
CREATE TABLE IF NOT EXISTS `payments` (
  `id`                bigint        NOT NULL AUTO_INCREMENT,
  `amount`            decimal(10,2) NOT NULL,
  `convenience_fee`   decimal(10,2) NOT NULL,
  `masked_reference`  varchar(40)   DEFAULT NULL,   -- **** **** **** 1234 / 07******67
  `method`            enum('CARD','CASH_COUNTER','WALLET') NOT NULL,
  `paid_at`           datetime(6)   NOT NULL,
  `refunded_amount`   decimal(10,2) NOT NULL,
  `status`            enum('PARTIALLY_REFUNDED','REFUNDED','SUCCESS') NOT NULL,
  `transaction_ref`   varchar(40)   NOT NULL,       -- TXN-CARD-XXXXXXXX
  `booking_id`        bigint        NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjwk4x78utnlycetjq9ulvm7tr` (`transaction_ref`),
  KEY `FKc52o2b1jkxttngufqp3t7jr3h` (`booking_id`),
  CONSTRAINT `FKc52o2b1jkxttngufqp3t7jr3h`
    FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One review per booking (unique booking_id).
CREATE TABLE IF NOT EXISTS `reviews` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `booking_id`  bigint       NOT NULL,
  `comment`     varchar(500) DEFAULT NULL,
  `created_at`  datetime(6)  NOT NULL,
  `rating`      int          NOT NULL,
  `customer_id` bigint       NOT NULL,
  `movie_id`    bigint       NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3p9j9vyr1qofbcxju65es206r` (`booking_id`),
  KEY `FKkquncb1glvrldaui8v52xfd5q` (`customer_id`),
  KEY `FK87tlqya0rq8ijfjscldpvvdyq` (`movie_id`),
  CONSTRAINT `FK87tlqya0rq8ijfjscldpvvdyq`
    FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`),
  CONSTRAINT `FKkquncb1glvrldaui8v52xfd5q`
    FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The customer inbox (booking confirmed, moved, cancelled, hold expired, reminders).
CREATE TABLE IF NOT EXISTS `notifications` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `booking_id`        bigint       DEFAULT NULL,
  `booking_reference` varchar(20)  DEFAULT NULL,
  `created_at`        datetime(6)  NOT NULL,
  `message`           varchar(255) NOT NULL,
  `read_at`           datetime(6)  DEFAULT NULL,
  `type`              enum('BOOKING_CANCELLED','BOOKING_CONFIRMED','BOOKING_MOVED',
                           'HOLD_EXPIRED','REMINDER_24H','REMINDER_2H') NOT NULL,
  `customer_id`       bigint       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc94cts1ygm7394xj40x1yov1k` (`customer_id`),
  CONSTRAINT `FKc94cts1ygm7394xj40x1yov1k`
    FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Member 1: login lockout counters, keyed by a hash of the email (never the email itself).
CREATE TABLE IF NOT EXISTS `login_attempts` (
  `email_hash`   varchar(64) NOT NULL,
  `failures`     int         NOT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `window_start` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`email_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -------------------------------------------------------------------------------------
-- SECTION 3B -- the Tier 1-3 upgrade tables
--
--   promo_codes        discount codes (a percentage or a flat amount, minimum spend, validity, use limit)
--   wallet_transactions  the wallet ledger: TOP_UP, PAYMENT and REFUND rows, with the balance after each
--   waitlist_entries   "notify me" for a sold-out show, first come first served
--
--   Hibernate (ddl-auto=update) also creates these on startup, so running this section is optional -
--   it just means a brand-new database is complete before the application is started for the first time.
-- -------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `promo_codes` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `code`        varchar(30)   NOT NULL,
  `description` varchar(160)  DEFAULT NULL,
  `type`        enum('FLAT','PERCENT') NOT NULL,
  `value`       decimal(10,2) NOT NULL,
  `min_spend`   decimal(10,2) NOT NULL DEFAULT 0.00,
  `valid_from`  date          NOT NULL,
  `valid_until` date          NOT NULL,
  `max_uses`    int           DEFAULT NULL,             -- NULL = unlimited
  `used_count`  int           NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_promo_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `wallet_transactions` (
  `id`            bigint        NOT NULL AUTO_INCREMENT,
  `customer_id`   bigint        NOT NULL,
  `type`          enum('PAYMENT','REFUND','TOP_UP') NOT NULL,
  `amount`        decimal(10,2) NOT NULL,               -- positive = money in, negative = money out
  `balance_after` decimal(10,2) NOT NULL,               -- the balance once this row was written
  `note`          varchar(120)  DEFAULT NULL,
  `created_at`    datetime(6)   NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wallet_customer_created` (`customer_id`, `created_at`),
  CONSTRAINT `fk_wallet_customer` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `waitlist_entries` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `show_id`     bigint      NOT NULL,
  `customer_id` bigint      NOT NULL,
  `joined_at`   datetime(6) NOT NULL,
  `notified_at` datetime(6) DEFAULT NULL,               -- set when seats came back and we told them
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waitlist_customer_show` (`show_id`, `customer_id`),
  KEY `idx_waitlist_show_joined` (`show_id`, `joined_at`),
  KEY `fk_waitlist_customer` (`customer_id`),
  CONSTRAINT `fk_waitlist_customer` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_waitlist_show` FOREIGN KEY (`show_id`) REFERENCES `showtimes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The columns the upgrade added to tables that already existed.
-- MySQL 8 has no "ADD COLUMN IF NOT EXISTS", so these are wrapped in a stored procedure that
-- checks information_schema first: the script can therefore be run twice without an error.
DROP PROCEDURE IF EXISTS `add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_column_if_missing`(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('users', 'wallet_balance', 'decimal(10,2) DEFAULT NULL');
CALL add_column_if_missing('bookings', 'discount_amount', 'decimal(10,2) DEFAULT NULL');
CALL add_column_if_missing('bookings', 'promo_code', 'varchar(30) DEFAULT NULL');
CALL add_column_if_missing('bookings', 'checked_in_at', 'datetime(6) DEFAULT NULL');
CALL add_column_if_missing('bookings', 'checked_in_by', 'varchar(60) DEFAULT NULL');

DROP PROCEDURE IF EXISTS `add_column_if_missing`;

-- The booking status gained CHECKED_IN (scanned at the door). Widening an enum is safe: no row can be
-- lost, the three old values keep their meaning.
ALTER TABLE `bookings`
    MODIFY COLUMN `status` enum('CANCELLED','CHECKED_IN','CONFIRMED','PENDING_PAYMENT') NOT NULL;

-- -------------------------------------------------------------------------------------
-- SECTION 3C -- the indexes that make the bigger data sets fast
--
--   With a few hundred bookings the database does not care. With the demo history (5 000 bookings,
--   540 past showtimes) these indexes are the difference between a table scan and a lookup.
--   The names match the @Index annotations on the entities, so Hibernate finds them already there.
-- -------------------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_index_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_index_if_missing`(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('bookings', 'idx_booking_customer_created', 'customer_id, created_at');
CALL add_index_if_missing('bookings', 'idx_booking_show_status', 'show_id, status');
CALL add_index_if_missing('bookings', 'idx_booking_checked_in', 'checked_in_at');
CALL add_index_if_missing('showtimes', 'idx_showtime_movie_start', 'movie_id, start_time');
CALL add_index_if_missing('showtimes', 'idx_showtime_screen_start', 'screen_id, start_time');
CALL add_index_if_missing('payments', 'idx_payment_method_paid', 'method, paid_at');
CALL add_index_if_missing('payments', 'idx_payment_booking', 'booking_id');
CALL add_index_if_missing('reviews', 'idx_review_movie_created', 'movie_id, created_at');
CALL add_index_if_missing('reviews', 'idx_review_customer', 'customer_id');
CALL add_index_if_missing('notifications', 'idx_notification_customer_created', 'customer_id, created_at');
CALL add_index_if_missing('notifications', 'idx_notification_unread', 'customer_id, read_at');

DROP PROCEDURE IF EXISTS `add_index_if_missing`;

-- -------------------------------------------------------------------------------------
-- SECTION 4 -- the three MySQL import tables, and the sample rows they start with
--
--   These are the tables the Import buttons read. They are ordinary MySQL tables:
--     import_movies     -> copied into  movies      by  POST /api/admin/files/import-movies
--     import_users      -> copied into  users       by  POST /api/admin/files/import-users
--     import_showtimes  -> copied into  showtimes   by  POST /api/admin/files/import-showtimes
--
--   To add data of your own, INSERT into these tables (or edit the rows in Workbench) and
--   press the matching button in Admin -> Files & Reports. No file is involved at any point.
--
--   The showtime dates are written relative to CURDATE(), so they are always in the future
--   no matter when this script is run.
-- -------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `import_movies` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `title`            VARCHAR(150) NOT NULL,
    `genre`            VARCHAR(60)  DEFAULT NULL,
    `language`         VARCHAR(40)  DEFAULT NULL,
    `duration_minutes` INT          DEFAULT NULL,
    `rating`           VARCHAR(20)  DEFAULT NULL,
    `movie_format`     VARCHAR(20)  DEFAULT NULL,
    `release_date`     DATE         DEFAULT NULL,
    `director`         VARCHAR(150) DEFAULT NULL,
    `producers`        VARCHAR(300) DEFAULT NULL,
    `writers`          VARCHAR(300) DEFAULT NULL,
    `cast_members`     VARCHAR(500) DEFAULT NULL,
    `trailer_url`      VARCHAR(500) DEFAULT NULL,
    `still_urls`       VARCHAR(2000) DEFAULT NULL,
    `description`      VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `import_users` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `account_type`     VARCHAR(20)  NOT NULL,          -- ADMIN or CUSTOMER
    `full_name`        VARCHAR(150) NOT NULL,
    `email`            VARCHAR(160) NOT NULL,
    `password_hash`    VARCHAR(100) NOT NULL,          -- BCrypt hash, never a readable password
    `phone`            VARCHAR(30)  DEFAULT NULL,
    `permission_level` VARCHAR(30)  DEFAULT NULL,      -- SUPER_ADMIN, STAFF or BOX_OFFICE, empty for customers
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `import_showtimes` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `movie_title`  VARCHAR(150)  NOT NULL,            -- must match a title in movies (or import_movies)
    `theatre_name` VARCHAR(120)  NOT NULL,
    `screen_name`  VARCHAR(120)  NOT NULL,
    `start_time`   DATETIME(6)   NOT NULL,
    `base_price`   DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Sample movies (6). Only inserted when the table is still empty, so re-running is safe.
INSERT INTO `import_movies`
    (`title`, `genre`, `language`, `duration_minutes`, `rating`, `movie_format`, `release_date`,
     `director`, `producers`, `writers`, `cast_members`, `trailer_url`, `still_urls`, `description`)
SELECT * FROM (
    SELECT 'Midnight Harbor' AS title, 'Thriller' AS genre, 'English' AS language, 118 AS duration_minutes, 'PG-13' AS rating,
           'STANDARD' AS movie_format, '2026-09-12' AS release_date, 'Nadia Fernando' AS director, 'Ruwan Silva, Lisa Brooks' AS producers,
           'Dinesh Perera, Carla Mendes' AS writers, 'Sam Whitaker, Priya Nair, Tom Keller, Amara Okafor' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/midnight1/960/540,https://picsum.photos/seed/midnight2/960/540,https://picsum.photos/seed/midnight3/960/540,https://picsum.photos/seed/midnight4/960/540' AS still_urls, 'A detective races against the tide to solve a harbour murder before the ferry returns at dawn. As the fog rolls in, every suspect on the pier has a secret worth killing for.' AS description
    UNION ALL
    SELECT 'Starfall Legacy' AS title, 'Sci-Fi' AS genre, 'English' AS language, 152 AS duration_minutes, 'PG-13' AS rating,
           'IMAX' AS movie_format, '2026-09-26' AS release_date, 'Elena Vasquez' AS director, 'Marcus Hale, Yuki Tan' AS producers,
           'Owen Price, Hana Lindqvist' AS writers, 'Jordan Blake, Sofia Ortiz, Ravi Shah, Ingrid Moss' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/starfall1/960/540,https://picsum.photos/seed/starfall2/960/540,https://picsum.photos/seed/starfall3/960/540,https://picsum.photos/seed/starfall4/960/540' AS still_urls, 'A crew of explorers discovers a dying star that holds the key to humanity''s future. Their journey to the edge of the galaxy will test every bond they have.' AS description
    UNION ALL
    SELECT 'Kandy Dreams' AS title, 'Drama' AS genre, 'Sinhala' AS language, 124 AS duration_minutes, 'U' AS rating,
           'STANDARD' AS movie_format, '2026-10-02' AS release_date, 'Chamara Wickramasinghe' AS director, 'Anusha Jayasuriya' AS producers,
           'Sanath Gunawardena' AS writers, 'Dilani Ratnayake, Kasun Herath, Sachini Perera' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/kandy1/960/540,https://picsum.photos/seed/kandy2/960/540,https://picsum.photos/seed/kandy3/960/540' AS still_urls, 'A young tea-estate musician chases her dream of performing in the city. Far from the hills she grew up in, she must decide between her family and her music.' AS description
    UNION ALL
    SELECT 'Ocean of Echoes' AS title, 'Animation' AS genre, 'English' AS language, 96 AS duration_minutes, 'U' AS rating,
           'THREE_D' AS movie_format, '2026-10-03' AS release_date, 'Mia Chen' AS director, 'Leo Duarte' AS producers,
           'Priya Sen, Hugo Lambert' AS writers, 'Voices: Zara Lim, Benji Oko, Nora Vance' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/ocean1/960/540,https://picsum.photos/seed/ocean2/960/540,https://picsum.photos/seed/ocean3/960/540,https://picsum.photos/seed/ocean4/960/540' AS still_urls, 'A lonely whale and a curious girl share an adventure beneath the waves. Together they search for the song that once called the whales home.' AS description
    UNION ALL
    SELECT 'The Last Monsoon' AS title, 'Romance' AS genre, 'Tamil' AS language, 130 AS duration_minutes, 'PG' AS rating,
           'STANDARD' AS movie_format, '2026-09-18' AS release_date, 'Arjun Raman' AS director, 'Meena Subramaniam' AS producers,
           'Arjun Raman, Divya Krish' AS writers, 'Vijay Anand, Lakshmi Iyer, Karthik Nair' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/monsoon1/960/540,https://picsum.photos/seed/monsoon2/960/540,https://picsum.photos/seed/monsoon3/960/540' AS still_urls, 'Two old friends reunite during the final monsoon of the year and rediscover their love. Between the rain and the old letters, they must face the choices they made long ago.' AS description
    UNION ALL
    SELECT 'Neon Circuit' AS title, 'Action' AS genre, 'English' AS language, 141 AS duration_minutes, '13+' AS rating,
           'IMAX' AS movie_format, '2026-10-09' AS release_date, 'Rachel Okonkwo' AS director, 'Daniel Park, Frank Mirza' AS producers,
           'Luis Ferreira, Ava Stone' AS writers, 'Maya Chandler, Ethan Cole, Nina Sato, Omar Haddad' AS cast_members, NULL AS trailer_url, 'https://picsum.photos/seed/neon1/960/540,https://picsum.photos/seed/neon2/960/540,https://picsum.photos/seed/neon3/960/540,https://picsum.photos/seed/neon4/960/540,https://picsum.photos/seed/neon5/960/540' AS still_urls, 'A rogue hacker must outrun a city-wide surveillance grid to save her younger brother. Every street camera is watching, and the system is learning fast.' AS description
) AS sample_movies
WHERE NOT EXISTS (SELECT 1 FROM `import_movies`);

-- Sample accounts (4): 1 SUPER_ADMIN, 1 STAFF, 2 customers. Passwords are BCrypt hashes already.
INSERT INTO `import_users`
    (`account_type`, `full_name`, `email`, `password_hash`, `phone`, `permission_level`)
SELECT * FROM (
    SELECT 'ADMIN' AS account_type, 'Super Admin' AS full_name, 'admin@cinema.lk' AS email, '$2a$10$9ghi64Bb39fC4Ui5Jyyfuei2baKEbFW400fmU.sJqwREKQUOf2Cue' AS password_hash, '0112000000' AS phone,
           'SUPER_ADMIN' AS permission_level
    UNION ALL
    SELECT 'ADMIN' AS account_type, 'Front Desk Staff' AS full_name, 'staff@cinema.lk' AS email, '$2a$10$2Xthtx7y5JEsQhsfNdbFxe.O4gUOiju2CLzksClWsONNF0Dt0uiLy' AS password_hash, '0112000001' AS phone,
           'STAFF' AS permission_level
    UNION ALL
    SELECT 'CUSTOMER' AS account_type, 'Kasun Perera' AS full_name, 'customer@cinema.lk' AS email, '$2a$10$.uiBbX2pqyO5sArfb7StWuLZXeNEm8y9PhuOeuKErW5MfcBqmh6ZG' AS password_hash, '0771234567' AS phone,
           NULL AS permission_level
    UNION ALL
    SELECT 'CUSTOMER' AS account_type, 'Nimali Silva' AS full_name, 'nimali@cinema.lk' AS email, '$2a$10$uuO2XqcPbELy0K6D6xOxj.Qay3C0OY6qy.qhI4HzMjxs5Z.JZx1Ka' AS password_hash, '0719876543' AS phone,
           NULL AS permission_level
    UNION ALL
    -- The door account: it may scan tickets and look up bookings, nothing else (see AuthInterceptor).
    SELECT 'ADMIN' AS account_type, 'Door Staff' AS full_name, 'door@cinema.lk' AS email, '$2a$10$80sPcI9micCAgkbiuYJRYuhVADE0fMX7FPQ8TlBliE8wzeykkVJ52' AS password_hash, '0112000002' AS phone,
           'BOX_OFFICE' AS permission_level
) AS sample_users
WHERE NOT EXISTS (SELECT 1 FROM `import_users`);

-- Sample showtimes (36), dated from tomorrow so they are never in the past.
INSERT INTO `import_showtimes`
    (`movie_title`, `theatre_name`, `screen_name`, `start_time`, `base_price`)
SELECT * FROM (
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Kandy Dreams' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Midnight Harbor' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Neon Circuit' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Negombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Ocean of Echoes' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 1 - Standard' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'Starfall Legacy' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 2 - Premium' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
    UNION ALL
    SELECT 'The Last Monsoon' AS movie_title, 'CineMax Colombo' AS theatre_name, 'Screen 3 - VIP' AS screen_name,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00') AS start_time, 1500.00 AS base_price
) AS sample_showtimes
WHERE NOT EXISTS (SELECT 1 FROM `import_showtimes`);

-- How many rows are waiting in each import table?
SELECT 'import_movies' AS table_name, COUNT(*) AS rows_waiting FROM `import_movies`
UNION ALL SELECT 'import_users', COUNT(*) FROM `import_users`
UNION ALL SELECT 'import_showtimes', COUNT(*) FROM `import_showtimes`;

-- -------------------------------------------------------------------------------------
-- SECTION 5 -- verify
-- -------------------------------------------------------------------------------------
SELECT 'database created' AS step, SCHEMA_NAME AS name, DEFAULT_CHARACTER_SET_NAME AS charset,
       DEFAULT_COLLATION_NAME AS collation
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'movie_booking';

SELECT COUNT(*) AS tables_created
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'movie_booking';

SELECT TABLE_NAME AS table_name, ENGINE AS engine, TABLE_COLLATION AS collation
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'movie_booking'
ORDER BY TABLE_NAME;

-- -------------------------------------------------------------------------------------
--  Expected: tables_created = 17 (14 application tables + 3 import tables) and every
--  collation utf8mb4_0900_ai_ci.
--
--  NEXT STEPS
--    1. Start the app:  mvn spring-boot:run
--       It copies the sample rows out of the import tables (5 users, 6 movies, 36 showtimes),
--       creates 2 theatres and 6 screens, and serves http://localhost:8080
--    2. If you created the cinemax user above, set these before starting (IntelliJ:
--       Run -> Edit Configurations -> Environment variables):
--           DB_USER=cinemax
--           DB_PASS=Cinemax#2026
--       If you prefer root, set DB_PASS to your root password and leave DB_USER as root.
--    3. Check the data:  SELECT reference, status, total_amount FROM bookings;
--
--  START OVER (throws away all data):
--       DROP DATABASE movie_booking;   then run this script again.
-- -------------------------------------------------------------------------------------
