package com.moviebooking.waitlist;

import com.moviebooking.booking.dto.BookingDto.BookingRequest;
import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import com.moviebooking.waitlist.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 waitlist ("notify me") for a sold-out show.
 *
 * <p>When every seat of a show is held, the movie page offers the waitlist instead of a booking button.
 * The moment a booking is cancelled the seats are free again and the waiting customers are told, in the
 * order they joined - and their entry is marked as notified, which is what gives them the short priority
 * window over somebody who just arrives at the page.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class WaitlistTest {

    @Autowired WaitlistService waitlist;
    @Autowired com.moviebooking.booking.service.BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired ShowtimeRepository showtimes;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aSoldOutShowTakesAWaitlistAndNotifiesWhenSeatsComeBack() {
        // The smallest screen, moved into the future, so filling it up is cheap.
        Long showId = jdbc.queryForObject(
                "SELECT s.id FROM showtimes s JOIN screens sc ON sc.id = s.screen_id " +
                        "ORDER BY sc.row_count * sc.seats_per_row ASC, s.id ASC LIMIT 1", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(8), showId);
        Showtime show = showtimes.findById(showId).orElseThrow();
        int rows = show.getScreen().getRowCount();
        int perRow = show.getScreen().getSeatsPerRow();

        Customer holder = newCustomer("Holder");
        Customer waiter = newCustomer("Waiter");

        // While seats are free the waitlist refuses politely: book them instead.
        RuntimeException tooEarly = assertThrows(RuntimeException.class,
                () -> waitlist.join(waiter.getId(), showId));
        assertTrue(tooEarly.getMessage().contains("Seats are available"));

        // Fill the screen seat by seat, ten at a time (the booking limit).
        List<String> free = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 1; c <= perRow; c++) {
                free.add((char) ('A' + r) + String.valueOf(c));
            }
        }
        List<Long> bookingIds = new ArrayList<>();
        for (int i = 0; i < free.size(); i += 10) {
            List<String> chunk = free.subList(i, Math.min(i + 10, free.size()));
            bookingIds.add(bookingService.create(holder.getId(), new BookingRequest(showId, chunk, null)).id());
        }
        assertEquals(rows * perRow, free.size());

        // Now it is sold out, and the waitlist takes the customer.
        var entry = waitlist.join(waiter.getId(), showId);
        assertEquals(showId, entry.showId());
        assertEquals(1, waitlist.mine(waiter.getId()).size());

        // Cancelling one of the holds frees seats; a paid cancellation is what tells the waiters.
        Long paidBooking = bookingIds.get(0);
        Booking confirmed = bookings.findById(paidBooking).orElseThrow();
        confirmed.markConfirmed();
        bookings.save(confirmed);
        Booking kept = bookings.findById(bookingIds.get(1)).orElseThrow();
        bookingService.cancel(holder.getId(), kept.getId());

        int told = waitlist.notifyFreedSeats(show, 10);
        assertTrue(told >= 1, "the waiting customer should be notified");

        Integer notes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE customer_id = ? AND type = 'WAITLIST_SEAT_FREE'",
                Integer.class, waiter.getId());
        assertTrue(notes >= 1, "a WAITLIST_SEAT_FREE notification is written");
        assertTrue(waitlist.mine(waiter.getId()).get(0).notified(),
                "the entry is marked as notified, which is what gives the priority window");
    }

    private Customer newCustomer(String name) {
        return customers.save(new Customer(name + " " + UUID.randomUUID().toString().substring(0, 6),
                "wait-" + UUID.randomUUID() + "@cinema.lk",
                "$2a$10$9ghi64Bb39fC4Ui5Jyyfuei2baKEbFW400fmU.sJqwREKQUOf2Cue", "0770000009"));
    }
}
