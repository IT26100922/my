package com.moviebooking.booking;

import com.moviebooking.booking.dto.BookingDto.BookingRequest;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The important concurrency test of the booking module.
 *
 * <p>Two customers press "Book" on the same seat at the same moment. Without protection both requests
 * would read an empty seat row, both would think the seat is free, and the cinema would sell it twice.
 * {@code BookingService.create} first takes a {@code PESSIMISTIC_WRITE} lock on the showtime row, so the
 * second transaction waits at the door until the first one has written its booking.</p>
 *
 * <p>The test starts the two calls on a latch so they really overlap, then asserts that exactly one of
 * them succeeded and that the seat exists exactly once in the database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SeatConcurrencyTest {

    @Autowired BookingService bookingService;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwoThreadsCanBookTheSameSeat() throws Exception {
        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?",
                LocalDateTime.now().plusDays(5), showId);

        Customer alice = customers.save(new Customer("Race Alice", uniqueEmail(), hash(), "0770000001"));
        Customer bob = customers.save(new Customer("Race Bob", uniqueEmail(), hash(), "0770000002"));
        String seat = "C7";

        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refusals = new AtomicInteger();

        Callable<Void> attempt = () -> {
            startLine.await(5, TimeUnit.SECONDS);
            try {
                bookingService.create(Thread.currentThread().getName().endsWith("a") ? alice.getId() : bob.getId(),
                        new BookingRequest(showId, List.of(seat), null));
                successes.incrementAndGet();
            } catch (RuntimeException refused) {
                refusals.incrementAndGet();
            }
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread t = new Thread(runnable);
            t.setName("race-" + UUID.randomUUID().toString().substring(0, 8));
            return t;
        });
        try {
            Future<Void> first = pool.submit(attempt);
            Future<Void> second = pool.submit(attempt);
            startLine.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get(), "exactly one booking should be created");
        assertEquals(1, refusals.get(), "the second attempt should be refused");

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking_seats bs JOIN bookings b ON b.id = bs.booking_id " +
                        "WHERE bs.seat_code = ? AND b.show_id = ? AND b.status <> 'CANCELLED'",
                Integer.class, seat, showId);
        assertEquals(1, rows.intValue(), "the seat must be held by exactly one booking");
        assertTrue(rows == 1);
    }

    private static String uniqueEmail() {
        return "race-" + UUID.randomUUID() + "@cinema.lk";
    }

    /** Any BCrypt hash will do: these rows are never used to log in. */
    private static String hash() {
        return "$2a$10$9ghi64Bb39fC4Ui5Jyyfuei2baKEbFW400fmU.sJqwREKQUOf2Cue";
    }
}
