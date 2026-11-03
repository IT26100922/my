package com.moviebooking.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Booking changes: cancellation preview and refund tiers, moving a booking to another showtime (rules, charge for
 * a higher price, refund for a lower price), and protection against two customers taking the same seat at once.
 * Each test copies its own showtimes so seats never clash with other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingChangesTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private MockHttpSession login(String prefix) throws Exception {
        String email = prefix + UUID.randomUUID() + "@test.lk";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Change Tester\",\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    /** Copies an existing showtime (same movie, screen and price) to a new start time, optionally at a new base price. */
    private long copyShow(long fromId, LocalDateTime start, BigDecimal basePrice) {
        jdbc.update("INSERT INTO showtimes (movie_id, screen_id, start_time, end_time, base_price) "
                        + "SELECT movie_id, screen_id, ?, ?, ? FROM showtimes WHERE id = ?",
                start, start.plusHours(2), basePrice, fromId);
        return jdbc.queryForObject("SELECT MAX(id) FROM showtimes", Long.class);
    }

    private long basePriceOf(long showId) {
        return jdbc.queryForObject("SELECT base_price FROM showtimes WHERE id = ?", Long.class, showId);
    }

    private long seedShowId() {
        return jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
    }

    private JsonNode book(MockHttpSession s, long showId, String... seats) throws Exception {
        List<String> quoted = new ArrayList<>();
        for (String seat : seats) quoted.add("\"" + seat + "\"");
        MvcResult r = mvc.perform(post("/api/customer/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[" + String.join(",", quoted) + "]}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private void pay(MockHttpSession s, long bookingId) throws Exception {
        mvc.perform(post("/api/customer/payments").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private void setStart(long showId, LocalDateTime start) {
        jdbc.update("UPDATE showtimes SET start_time = ?, end_time = ? WHERE id = ?", start, start.plusHours(2), showId);
    }

    private JsonNode preview(MockHttpSession s, long bookingId) throws Exception {
        MvcResult r = mvc.perform(get("/api/customer/bookings/" + bookingId + "/cancel-preview").session(s))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void cancelPreviewFollowsTheRefundTiers() throws Exception {
        long src = seedShowId();
        long show = copyShow(src, LocalDateTime.now().plusDays(3), new BigDecimal(basePriceOf(src)));
        MockHttpSession s = login("tiers-");
        JsonNode booking = book(s, show, "C5");
        long bookingId = booking.get("id").asLong();
        BigDecimal total = booking.get("totalAmount").decimalValue();
        pay(s, bookingId);

        // 24 hours or more before the show: full refund
        assertEquals(0, total.compareTo(preview(s, bookingId).get("refundAmount").decimalValue()));

        // 2 to 24 hours before: 50%
        setStart(show, LocalDateTime.now().plusHours(6));
        BigDecimal half = total.multiply(new BigDecimal("0.5")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, half.compareTo(preview(s, bookingId).get("refundAmount").decimalValue()));

        // under 2 hours: no refund
        setStart(show, LocalDateTime.now().plusHours(1));
        assertEquals(0, BigDecimal.ZERO.compareTo(preview(s, bookingId).get("refundAmount").decimalValue()));
    }

    @Test
    void moveRulesAreEnforced() throws Exception {
        long src = seedShowId();
        long base = basePriceOf(src);
        // weekend shows carry a higher price, so both shows are placed on weekdays to keep the price equal
        LocalDateTime fromTime = weekday(LocalDateTime.now().plusDays(3));
        long from = copyShow(src, fromTime, new BigDecimal(base));
        long to = copyShow(src, weekday(fromTime.plusDays(1)), new BigDecimal(base));
        MockHttpSession s = login("rules-");
        JsonNode booking = book(s, from, "C6");
        long bookingId = booking.get("id").asLong();
        pay(s, bookingId);

        // same showtime
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + from + ",\"seatCodes\":[\"C6\"]}"))
                .andExpect(status().isBadRequest());
        // seat count must match the booking
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + to + ",\"seatCodes\":[\"C6\",\"C7\"]}"))
                .andExpect(status().isBadRequest());
        // a different film is not allowed
        long otherFilm = jdbc.queryForObject("SELECT id FROM showtimes WHERE movie_id <> "
                + "(SELECT movie_id FROM showtimes WHERE id = " + from + ") ORDER BY id LIMIT 1", Long.class);
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + otherFilm + ",\"seatCodes\":[\"C6\"]}"))
                .andExpect(status().isBadRequest());

        // valid move to a same-price show
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + to + ",\"seatCodes\":[\"C6\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(to))
                .andExpect(jsonPath("$.seatCodes[0]").value("C6"));

        // under 2 hours before the original show: the move is refused
        long late = copyShow(src, LocalDateTime.now().plusHours(1), new BigDecimal(base));
        JsonNode lateBooking = book(s, late, "C8");
        pay(s, lateBooking.get("id").asLong());
        mvc.perform(post("/api/customer/bookings/" + lateBooking.get("id").asLong() + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + to + ",\"seatCodes\":[\"C8\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void higherPriceNeedsPaymentAndLowerPriceIsRefunded() throws Exception {
        long src = seedShowId();
        long base = basePriceOf(src);
        long cheap = copyShow(src, LocalDateTime.now().plusDays(3), new BigDecimal(base));
        long dear = copyShow(src, LocalDateTime.now().plusDays(4), new BigDecimal(base * 2));
        MockHttpSession s = login("price-");
        JsonNode booking = book(s, cheap, "D4");
        long bookingId = booking.get("id").asLong();
        BigDecimal cheapTotal = booking.get("totalAmount").decimalValue();
        pay(s, bookingId);

        // upgrade without a payment method is refused
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + dear + ",\"seatCodes\":[\"D4\"]}"))
                .andExpect(status().isBadRequest());

        // upgrade with a card: the difference is charged as a second payment
        MvcResult up = mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + dear + ",\"seatCodes\":[\"D4\"],\"method\":\"CARD\","
                                + "\"reference\":\"4111111111111111\"}"))
                .andExpect(status().isOk()).andReturn();
        BigDecimal dearTotal = mapper.readTree(up.getResponse().getContentAsString()).get("totalAmount").decimalValue();
        assertTrue(dearTotal.compareTo(cheapTotal) > 0);
        Integer payments = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ?", Integer.class, bookingId);
        assertEquals(2, payments);

        // downgrade back to the cheaper show: the difference is refunded to the payments
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/reschedule").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + cheap + ",\"seatCodes\":[\"D4\"]}"))
                .andExpect(status().isOk());
        BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(SUM(refunded_amount), 0) FROM payments WHERE booking_id = ?",
                BigDecimal.class, bookingId);
        assertEquals(0, dearTotal.subtract(cheapTotal).compareTo(refunded));

        // cancelling now refunds the cheaper total, so the booking ends fully refunded and nothing is over-refunded
        mvc.perform(delete("/api/customer/bookings/" + bookingId).session(s)).andExpect(status().isOk());
        BigDecimal totalRefunded = jdbc.queryForObject("SELECT COALESCE(SUM(refunded_amount), 0) FROM payments WHERE booking_id = ?",
                BigDecimal.class, bookingId);
        assertEquals(0, dearTotal.compareTo(totalRefunded));
    }

    @Test
    void twoCustomersCannotTakeTheSameSeatAtOnce() throws Exception {
        long src = seedShowId();
        long show = copyShow(src, LocalDateTime.now().plusDays(5), new BigDecimal(basePriceOf(src)));
        MockHttpSession first = login("race-a-");
        MockHttpSession second = login("race-b-");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (MockHttpSession s : List.of(first, second)) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/customer/bookings").session(s).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"showId\":" + show + ",\"seatCodes\":[\"E9\"]}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get(60, TimeUnit.SECONDS) == 201) created++;
        }
        pool.shutdown();
        assertEquals(1, created, "exactly one customer may book the seat");
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }

    private static void assertTrue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }

    private static LocalDateTime weekday(LocalDateTime t) {
        while (t.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || t.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            t = t.plusDays(1);
        }
        return t;
    }
}
