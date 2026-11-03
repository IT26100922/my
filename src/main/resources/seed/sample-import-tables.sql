-- =====================================================================================
-- Sample rows for the three MySQL import tables - the built-in copy of SECTION 4 of
-- database/cinemax_mysql_setup.sql.
--
--   It runs automatically on the first start when the import tables are still empty, so a brand-new
--   database always has the demo accounts and something to book, even if nobody ran the setup script.
--   Without it a database created by Hibernate alone has no accounts, and every login fails with
--   "Invalid email or password" - which looks like a broken login and is really an empty database.
--
--   Every statement inserts only when its table is empty, so running it twice is harmless. Nothing is
--   read from a text file at runtime: the application copies these rows into the live tables through
--   the same import path the Import buttons use.
--
--   සිංහල: පළමු වරට ධාවනය වන විට ආයාත වගු හිස් නම්, මෙම ආදර්ශ දත්ත ස්වයංක්‍රීයව ඇතුළත් වේ. එමනිසා
--   admin@cinema.lk / admin123 ගිණුම සැමවිටම තිබේ.
-- =====================================================================================

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
