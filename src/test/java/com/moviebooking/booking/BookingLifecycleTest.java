package com.moviebooking.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money and time rules, run end to end against the real services and the test database:
 * <ul>
 *   <li>cancellation refunds at each notice period (full, half, none)</li>
 *   <li>the seat-hold sweep frees unpaid seats and tells the customer, and leaves paid bookings alone</li>
 *   <li>the reminder sweep sends one reminder per booking, even when run twice, and writes it to the outbox</li>
 * </ul>
 * Each test uses its own seeded showtime (moved to the time it needs), so the tests do not share seats.
 * Scheduled jobs are disabled in application-test.properties, so each test calls the sweeps itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingLifecycleTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired BookingService bookingService;
    @Autowired NotificationService notificationService;
    @Value("${app.email.outbox-dir}") String outboxDir;

    /** Picks a seeded showtime by position (0-based) and moves it to the given start time. */
    private long showAt(int position, LocalDateTime start) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM showtimes ORDER BY id LIMIT 1 OFFSET " + position, Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", start, id);
        return id;
    }

    private MockHttpSession register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@cinema.lk";
        String body = "{\"fullName\":\"Test Customer\",\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    /** Books seats for a show and returns the booking JSON. */
    private JsonNode book(MockHttpSession who, long showId, String... seats) throws Exception {
        String codes = "\"" + String.join("\",\"", seats) + "\"";
        MvcResult r = mvc.perform(post("/api/customer/bookings").session(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[" + codes + "]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private void pay(MockHttpSession who, long bookingId) throws Exception {
        mvc.perform(post("/api/customer/payments").session(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private static BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    /** Books, pays and cancels one seat with the given notice. Returns {amount paid, refund}. */
    private BigDecimal[] payThenCancel(String prefix, LocalDateTime showStart, int position) throws Exception {
        long showId = showAt(position, showStart);
        MockHttpSession who = register(prefix);
        JsonNode booking = book(who, showId, "C5");
        long bookingId = booking.get("id").asLong();
        BigDecimal paid = money(booking, "totalAmount");
        pay(who, bookingId);
        MvcResult r = mvc.perform(delete("/api/customer/bookings/" + bookingId).session(who))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(result.get("bookingStatus").asText()).isEqualTo("CANCELLED");
        return new BigDecimal[] {paid, money(result, "refundAmount")};
    }

    // ---------- fresh-install bookability ----------

    @Test
    void seededShowtimesAreBookableOnAFreshDatabase() {
        // The seed file's dates are fixed, so on a fresh install the import moves them into the future.
        Integer upcoming = jdbc.queryForObject(
                "SELECT COUNT(*) FROM showtimes WHERE start_time > ?", Integer.class, LocalDateTime.now());
        assertThat(upcoming).isGreaterThanOrEqualTo(3);
    }

    // ---------- refunds ----------

    @Test
    void cancellingThreeDaysAheadRefundsEverything() throws Exception {
        long showId = showAt(10, LocalDateTime.now().plusDays(3));
        MockHttpSession who = register("full");
        JsonNode booking = book(who, showId, "C5");
        BigDecimal total = money(booking, "totalAmount");
        pay(who, booking.get("id").asLong());

        MvcResult r = mvc.perform(delete("/api/customer/bookings/" + booking.get("id").asLong()).session(who))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(result.get("policy").asText()).startsWith("Full refund");
        assertThat(money(result, "refundAmount")).isEqualByComparingTo(total);
    }

    @Test
    void cancellingTwelveHoursAheadRefundsHalf() throws Exception {
        BigDecimal[] r = payThenCancel("half", LocalDateTime.now().plusHours(12), 11);
        BigDecimal paid = r[0];
        BigDecimal refund = r[1];
        assertThat(paid).isPositive();
        assertThat(refund).isEqualByComparingTo(paid.multiply(new BigDecimal("0.50")).setScale(2));
    }

    @Test
    void cancellingLessThanTwoHoursAheadRefundsNothing() throws Exception {
        BigDecimal[] r = payThenCancel("none", LocalDateTime.now().plusHours(1), 12);
        assertThat(r[1]).isEqualByComparingTo("0.00");
    }

    // ---------- seat hold expiry ----------

    @Test
    void expiredHoldReleasesSeatAndNotifiesButPaidBookingSurvives() throws Exception {
        long showId = showAt(13, LocalDateTime.now().plusDays(3));
        MockHttpSession alice = register("hold");
        JsonNode unpaid = book(alice, showId, "D4");
        long unpaidId = unpaid.get("id").asLong();
        JsonNode paid = book(alice, showId, "D5");
        pay(alice, paid.get("id").asLong());

        // Push the unpaid hold into the past. The paid booking has no hold.
        jdbc.update("UPDATE bookings SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), unpaidId);

        bookingService.releaseExpiredHolds();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class, unpaidId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class,
                paid.get("id").asLong())).isEqualTo(1);

        mvc.perform(get("/api/customer/notifications").session(alice))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].type").value("HOLD_EXPIRED"));

        // The released seat can now be booked by someone else.
        MockHttpSession bob = register("hold-bob");
        book(bob, showId, "D4");
    }

    // ---------- reminders ----------

    @Test
    void reminderSweepSendsOneReminderPerBookingAndWritesItToTheOutbox() throws Exception {
        long showId = showAt(14, LocalDateTime.now().plusMinutes(90)); // inside the 2-hour window
        MockHttpSession who = register("remind");
        JsonNode booking = book(who, showId, "A2");
        long bookingId = booking.get("id").asLong();
        String reference = booking.get("reference").asText();
        pay(who, bookingId);

        notificationService.runReminderSweep();
        notificationService.runReminderSweep(); // second run must not duplicate

        Integer reminders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE booking_id = ? AND type = 'REMINDER_2H'",
                Integer.class, bookingId);
        assertThat(reminders).isEqualTo(1);

        Path outbox = Path.of(outboxDir, "outbox.txt");
        assertThat(outbox).exists();
        String text = Files.readString(outbox, StandardCharsets.UTF_8);
        assertThat(text).contains("Subject: Show reminder " + reference);
    }
}
