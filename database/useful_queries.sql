-- =====================================================================================
--  CineMax - queries to run in MySQL Workbench
--  Open this file in Workbench (File -> Open SQL Script), choose the movie_booking
--  schema in the sidebar, then press the lightning bolt (or Ctrl+Shift+Enter) to run all,
--  or Ctrl+Enter to run only the statement under the cursor.
-- =====================================================================================

-- 0. Am I looking at the right database, and which tables exist?
SELECT DATABASE() AS current_database;
SHOW TABLES;                                  -- expect 14 tables (11 application + 3 import tables)

-- 1. The catalogue (Member 2)
SELECT id, title, genre, language, duration_minutes, rating, movie_format
FROM movies
ORDER BY title;

-- 2. Theatres and their screens (Member 3)
SELECT t.name AS theatre, t.city, s.name AS screen, s.screen_type,
       s.row_count, s.seats_per_row, (s.row_count * s.seats_per_row) AS seats
FROM screens s
JOIN theatres t ON t.id = s.theatre_id
ORDER BY t.name, s.name;

-- 3. Showtimes with movie, screen and theatre in one row (Member 4)
SELECT st.id, m.title, m.movie_format, t.name AS theatre, s.name AS screen,
       st.start_time, st.end_time, st.base_price
FROM showtimes st
JOIN movies m   ON m.id = st.movie_id
JOIN screens s  ON s.id = st.screen_id
JOIN theatres t ON t.id = s.theatre_id
ORDER BY st.start_time
LIMIT 30;

-- 4. Every user: one table holds customers and admins (Member 1)
SELECT id, user_type, full_name, email, phone, permission_level, loyalty_points, created_at
FROM users
ORDER BY user_type, id;

-- 5. Bookings with the customer and the movie (Member 5)
SELECT b.id, b.reference, b.status, b.total_amount, b.created_at, b.expires_at,
       u.full_name AS customer, m.title AS movie, st.start_time
FROM bookings b
JOIN users u    ON u.id = b.customer_id
JOIN showtimes st ON st.id = b.show_id
JOIN movies m   ON m.id = st.movie_id
ORDER BY b.created_at DESC;

-- 6. The seats of each booking (one row per seat: A1, A2 ...)
SELECT b.reference, bs.seat_code, b.status
FROM booking_seats bs
JOIN bookings b ON b.id = bs.booking_id
ORDER BY b.reference, bs.seat_code;

-- 7. Payments, including the card/wallet fee and the masked reference (Member 6)
SELECT p.id, p.transaction_ref, p.method, p.status,
       p.amount, p.convenience_fee, p.refunded_amount, p.masked_reference,
       b.reference AS booking, p.paid_at
FROM payments p
JOIN bookings b ON b.id = p.booking_id
ORDER BY p.paid_at DESC;

-- 8. Money by payment method: gross, fees and refunds
SELECT method,
       COUNT(*)                  AS payments,
       SUM(amount)               AS gross,
       SUM(convenience_fee)      AS fees,
       SUM(refunded_amount)      AS refunded
FROM payments
GROUP BY method;

-- 9. Seats sold per movie, ignoring cancelled bookings
SELECT m.title, COUNT(*) AS seats_sold, SUM(b.total_amount) AS value
FROM booking_seats bs
JOIN bookings b ON b.id = bs.booking_id
JOIN showtimes st ON st.id = b.show_id
JOIN movies m ON m.id = st.movie_id
WHERE b.status <> 'CANCELLED'
GROUP BY m.title
ORDER BY seats_sold DESC;

-- 10. Notifications in the customer inbox
SELECT n.id, u.email, n.type, n.message, n.created_at, n.read_at
FROM notifications n
JOIN users u ON u.id = n.customer_id
ORDER BY n.created_at DESC;

-- 11. Login lockout counters (emails are stored hashed, never in clear)
SELECT email_hash, failures, window_start, locked_until
FROM login_attempts;

-- 12. Reviews
SELECT r.id, m.title, u.full_name, r.rating, r.comment, r.created_at
FROM reviews r
JOIN movies m ON m.id = r.movie_id
JOIN users u  ON u.id = r.customer_id
ORDER BY r.created_at DESC;

-- 13. Quick counts for a demo
SELECT (SELECT COUNT(*) FROM movies)        AS movies,
       (SELECT COUNT(*) FROM showtimes)     AS showtimes,
       (SELECT COUNT(*) FROM bookings)      AS bookings,
       (SELECT COUNT(*) FROM payments)      AS payments,
       (SELECT COUNT(*) FROM users)         AS users;

-- 14. Empty a table's rows (careful: no undo). Prefer deleting through the admin page.
-- DELETE FROM reviews;
-- TRUNCATE TABLE login_attempts;

-- =====================================================================================
--  THE THREE MySQL IMPORT TABLES
--  The Import buttons copy these into the live tables. Nothing here comes from a file.
-- =====================================================================================

-- 15. How many rows are waiting in each import table, and how many are already live?
SELECT 'movies'    AS entity,
       (SELECT COUNT(*) FROM import_movies)    AS rows_in_import_table,
       (SELECT COUNT(*) FROM movies)           AS rows_live
UNION ALL SELECT 'users',
       (SELECT COUNT(*) FROM import_users),
       (SELECT COUNT(*) FROM users)
UNION ALL SELECT 'showtimes',
       (SELECT COUNT(*) FROM import_showtimes),
       (SELECT COUNT(*) FROM showtimes);

-- 16. What the Import movies button is about to add (titles not in the live table yet)
SELECT i.title, i.genre, i.movie_format, i.release_date
FROM import_movies i
WHERE NOT EXISTS (SELECT 1 FROM movies m WHERE m.title = i.title);

-- 17. Add your own movie to the staging table, then press Import movies
-- INSERT INTO import_movies (title, genre, language, duration_minutes, rating, movie_format,
--                            release_date, director, cast_members, description)
-- VALUES ('Blue Harbour', 'Drama', 'Sinhala', 118, 'PG-13', 'STANDARD',
--         '2026-12-05', 'Nadeesha Perera', 'Kasun Silva, Amali Fernando', 'A harbour town keeps one secret too many.');

-- 18. Add a customer account to the staging table, then press Import users.
--     password_hash is a BCrypt hash; a plain password also works and is hashed on the way in.
-- INSERT INTO import_users (account_type, full_name, email, password_hash, phone, permission_level)
-- VALUES ('CUSTOMER', 'Ishara Bandara', 'ishara@cinema.lk', 'ishara123', '0771234567', NULL);

-- 19. Add a screening to the staging table, then press Import showtimes.
--     The movie must exist in movies (or be imported first), and the screen must exist.
-- INSERT INTO import_showtimes (movie_title, theatre_name, screen_name, start_time, base_price)
-- VALUES ('Blue Harbour', 'CineMax Negombo', 'Screen 1 - Standard',
--         TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '19:00:00'), 1500.00);

-- 20. Showtimes in the staging table that are already in the past (the import skips them)
SELECT id, movie_title, start_time
FROM import_showtimes
WHERE start_time < NOW()
ORDER BY start_time;
