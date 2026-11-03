package com.moviebooking.report.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * READ: a live snapshot of the MySQL database, used by static/live.html for a real-time monitor.
 * Every call reads straight from the database with plain SQL, so what the page shows is what is stored.
 * Admin only: /api/admin/** is guarded by AuthInterceptor.
 */
@RestController
@RequestMapping("/api/admin/live")
public class LiveDatabaseController {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public LiveDatabaseController(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    /** Counts of every table, the newest rows, and which database the app is actually connected to. */
    @GetMapping
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("database", databaseInfo());
        out.put("counts", counts());
        out.put("bookings", jdbc.queryForList("""
                SELECT b.reference, b.status, b.total_amount, u.full_name AS customer, m.title AS movie,
                       (SELECT COUNT(*) FROM booking_seats bs WHERE bs.booking_id = b.id) AS seats,
                       DATE_FORMAT(b.created_at, '%Y-%m-%d %H:%i:%s') AS created
                FROM bookings b
                JOIN users u     ON u.id = b.customer_id
                JOIN showtimes st ON st.id = b.show_id
                JOIN movies m    ON m.id = st.movie_id
                ORDER BY b.id DESC LIMIT 6
                """));
        out.put("payments", jdbc.queryForList("""
                SELECT p.transaction_ref, p.method, p.status, p.amount, p.convenience_fee,
                       b.reference AS booking, p.masked_reference,
                       DATE_FORMAT(p.paid_at, '%Y-%m-%d %H:%i:%s') AS paid
                FROM payments p
                JOIN bookings b ON b.id = p.booking_id
                ORDER BY p.id DESC LIMIT 6
                """));
        out.put("movies", jdbc.queryForList("""
                SELECT id, title, genre, movie_format, duration_minutes
                FROM movies ORDER BY id DESC LIMIT 5
                """));
        out.put("serverTime", jdbc.queryForObject("SELECT DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')", String.class));
        return out;
    }

    private Map<String, Object> databaseInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            info.put("schema", c.getCatalog());
            info.put("product", c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
            info.put("url", c.getMetaData().getURL());
            info.put("user", c.getMetaData().getUserName());
            info.put("isolation", jdbc.queryForObject(
                    "SELECT @@transaction_isolation", String.class));
        } catch (SQLException | RuntimeException ex) {
            info.put("schema", "unknown");
            info.put("product", "unavailable: " + ex.getMessage());
        }
        return info;
    }

    private List<Map<String, Object>> counts() {
        return jdbc.queryForList("""
                SELECT 'movies' AS table_name, COUNT(*) AS rows_in_table FROM movies
                UNION ALL SELECT 'showtimes', COUNT(*) FROM showtimes
                UNION ALL SELECT 'theatres', COUNT(*) FROM theatres
                UNION ALL SELECT 'screens', COUNT(*) FROM screens
                UNION ALL SELECT 'users', COUNT(*) FROM users
                UNION ALL SELECT 'bookings', COUNT(*) FROM bookings
                UNION ALL SELECT 'booking_seats', COUNT(*) FROM booking_seats
                UNION ALL SELECT 'payments', COUNT(*) FROM payments
                UNION ALL SELECT 'reviews', COUNT(*) FROM reviews
                UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
                UNION ALL SELECT 'login_attempts', COUNT(*) FROM login_attempts
                """);
    }
}
