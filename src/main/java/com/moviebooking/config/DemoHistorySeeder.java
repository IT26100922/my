package com.moviebooking.config;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentMethodType;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.theatre.entity.Screen;
import com.moviebooking.theatre.repository.ScreenRepository;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Fills the database with a month of finished ticket sales, so the analytics page, the sales report and
 * the paged admin tables have something real to count.
 *
 * <p>Why this exists: the seed data only creates showtimes in the future, which means "revenue per day",
 * "occupancy by time slot" and the no-show rate all had nothing to read, and a paged table had one page.
 * This class replays a believable month: past showtimes, an audience, paid bookings, a few cancellations
 * and door scans for the tickets that were actually used.</p>
 *
 * <p>It runs only once — when no booking belongs to a show that has already started — and it can be
 * switched off with {@code cinemax.demo.history=false} (the test profile does that, so the tests stay fast
 * and their numbers stay predictable). The demo logins are deliberately left out of the history, so
 * "My bookings" still shows only what you created yourself.</p>
 */
@Component
@Order(2)
public class DemoHistorySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoHistorySeeder.class);

    /** How many finished bookings to replay: a full analytics page and a paged table to click through. */
    private static final int TARGET_BOOKINGS = 5000;
    private static final int PAST_DAYS = 30;
    private static final int DEMO_CUSTOMERS = 40;
    private static final BigDecimal CARD_FEE_RATE = new BigDecimal("0.02");

    /** Names used for the demo audience (the demo logins themselves never buy anything here). */
    private static final String[] FIRST_NAMES = {
            "Amara", "Nimal", "Kasun", "Dilini", "Suresh", "Tharindu", "Ishara", "Ruwan", "Sanduni", "Chathura",
            "Anjali", "Buddhika", "Malsha", "Dinesh", "Hasini", "Lahiru", "Nadeesha", "Pradeep", "Sachini", "Upul",
            "Yasara", "Kavindi", "Mahesh", "Nirosha", "Pasindu", "Rashmi", "Sameera", "Thilini", "Vishwa", "Ayesha",
            "Bhagya", "Chamara", "Devinda", "Erandi", "Gayan", "Hiruni", "Isuru", "Janani", "Kalana", "Lakmini"
    };
    private static final String[] LAST_NAMES = {
            "Perera", "Fernando", "Silva", "Jayasuriya", "Wickramasinghe", "Bandara", "Ranasinghe", "Gunawardena",
            "Herath", "Dissanayake", "Karunaratne", "Weerasinghe", "Ekanayake", "Rajapaksa", "Madushanka"
    };

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ShowtimeRepository showtimes;
    private final CustomerRepository customers;
    private final MovieRepository movies;
    private final ScreenRepository screens;
    private final ShowService showService;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public DemoHistorySeeder(BookingRepository bookings, PaymentRepository payments, ShowtimeRepository showtimes,
                             CustomerRepository customers, MovieRepository movies, ScreenRepository screens,
                             ShowService showService, PasswordEncoder passwordEncoder,
                             @Value("${cinemax.demo.history:true}") boolean enabled) {
        this.bookings = bookings;
        this.payments = payments;
        this.showtimes = showtimes;
        this.customers = customers;
        this.movies = movies;
        this.screens = screens;
        this.showService = showService;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Demo history seeding is switched off (cinemax.demo.history=false)");
            return;
        }
        if (bookings.countForPastShows(LocalDateTime.now()) > 0) {
            log.info("Sales history already exists, so no demo history was created");
            return;
        }
        List<Movie> movieList = movies.findAll();
        List<Screen> screenList = screens.findAll();
        if (movieList.isEmpty() || screenList.isEmpty()) {
            log.warn("No films or screens yet, so no demo history was created");
            return;
        }

        Random random = new Random(20260201L);   // fixed seed: the same demo data on every machine
        List<Customer> audience = seedAudience(random);
        List<Showtime> pastShows = seedPastShowtimes(movieList, screenList, random);
        int created = replayBookings(pastShows, audience, random);
        log.info("Demo history: {} past showtimes, {} bookings, {} customers", pastShows.size(), created,
                audience.size());
    }

    /* ---------- customers ---------- */

    /** A pool of ordinary customers, so the sales are not all made by the same two accounts. */
    private List<Customer> seedAudience(Random random) {
        List<Customer> audience = new ArrayList<>();
        String hash = passwordEncoder.encode("demo1234");
        for (int i = 0; i < DEMO_CUSTOMERS; i++) {
            String fullName = FIRST_NAMES[i % FIRST_NAMES.length] + " " + LAST_NAMES[(i * 7) % LAST_NAMES.length];
            String email = fullName.toLowerCase().replace(' ', '.') + i + "@mail.lk";
            Customer existing = customers
                    .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByFullNameAsc(email, email)
                    .stream().findFirst().orElse(null);
            if (existing != null) {
                audience.add(existing);
                continue;
            }
            Customer customer = new Customer(fullName, email, hash,
                    "07" + (random.nextInt(8) + 1) + String.format("%07d", random.nextInt(10_000_000)));
            customer.addLoyaltyPoints(random.nextInt(400));
            customer.creditWallet(new BigDecimal(random.nextInt(40) * 250 + 500).setScale(2));
            audience.add(customers.save(customer));
        }
        return audience;
    }

    /* ---------- past showtimes ---------- */

    /**
     * Three shows a day per screen for the last month. Base prices differ a little per screen, which is
     * what makes "revenue per screen" interesting instead of six identical bars.
     */
    private List<Showtime> seedPastShowtimes(List<Movie> movieList, List<Screen> screenList, Random random) {
        LocalTime[] slots = {LocalTime.of(10, 30), LocalTime.of(14, 15), LocalTime.of(18, 45)};
        List<Showtime> created = new ArrayList<>();
        for (int day = PAST_DAYS; day >= 1; day--) {
            LocalDate date = LocalDate.now().minusDays(day);
            for (int s = 0; s < screenList.size(); s++) {
                Screen screen = screenList.get(s);
                for (int slot = 0; slot < slots.length; slot++) {
                    Movie movie = movieList.get((day * 3 + s + slot) % movieList.size());
                    BigDecimal base = new BigDecimal(1200 + (s % 3) * 150 + (slot == 2 ? 200 : 0));
                    try {
                        created.add(showtimes.save(
                                new Showtime(movie, screen, LocalDateTime.of(date, slots[slot]), base)));
                    } catch (RuntimeException ex) {
                        log.debug("Skipped a demo showtime: {}", ex.getMessage());
                    }
                }
            }
        }
        return created;
    }

    /* ---------- bookings, payments and door scans ---------- */

    private int replayBookings(List<Showtime> pastShows, List<Customer> audience, Random random) {
        if (pastShows.isEmpty()) {
            return 0;
        }
        List<Set<String>> seatsTaken = new ArrayList<>();
        for (Showtime show : pastShows) {
            seatsTaken.add(bookings.findHeldSeatCodes(show.getId(), -1L, BookingStatus.activeStatuses(),
                    LocalDateTime.now()).stream().collect(java.util.stream.Collectors.toCollection(HashSet::new)));
        }
        List<Booking> batch = new ArrayList<>();
        List<Payment> paymentBatch = new ArrayList<>();
        int created = 0;
        int attempts = 0;

        while (created < TARGET_BOOKINGS && attempts < TARGET_BOOKINGS * 4) {
            attempts++;
            int index = random.nextInt(pastShows.size());
            Showtime show = pastShows.get(index);
            Set<String> taken = seatsTaken.get(index);
            List<String> free = new ArrayList<>(show.getScreen().getAllSeatCodes());
            free.removeAll(taken);
            if (free.size() < 6) {
                continue;   // this showing is effectively full, move on to another one
            }
            Customer customer = audience.get(random.nextInt(audience.size()));
            Set<String> chosen = pickSeats(free, random);
            if (chosen.isEmpty()) {
                continue;
            }
            taken.addAll(chosen);

            BigDecimal total = showService.totalForSeats(show, chosen);
            LocalDateTime paidAt = paidAtFor(show, random);
            Booking booking = new Booking(reference(created, random), customer, show, chosen, total, null);
            booking.backdateCreatedAt(paidAt.minusMinutes(2 + random.nextInt(25)));
            created++;

            if (random.nextInt(100) < 6) {
                // a cancellation: the seats go back to the pool and the money is refunded
                booking.markCancelled();
                batch.add(booking);
                paymentBatch.add(refundedPayment(booking, paidAt, random));
                taken.removeAll(chosen);
            } else {
                booking.markConfirmed();
                if (random.nextInt(100) >= 12) {
                    // scanned at the door; the other ~12% simply never turned up (the no-show rate)
                    booking.recordCheckIn(scanTimeFor(show, random), "CINEMA-DOOR");
                }
                batch.add(booking);
                paymentBatch.add(successfulPayment(booking, paidAt, random));
            }
            if (batch.size() >= 250) {
                flush(batch, paymentBatch);
            }
        }
        flush(batch, paymentBatch);
        return created;
    }

    /**
     * Writes the buffered rows in one go. Bookings are saved first: the payments point at them, so the
     * bookings need their ids before the payments can be written.
     */
    private void flush(List<Booking> batch, List<Payment> paymentBatch) {
        if (batch.isEmpty() && paymentBatch.isEmpty()) {
            return;
        }
        bookings.saveAll(batch);
        payments.saveAll(paymentBatch);
        batch.clear();
        paymentBatch.clear();
    }

    /** One to four seats, usually two that are next to each other (people book in pairs). */
    private Set<String> pickSeats(List<String> free, Random random) {
        int howMany = switch (random.nextInt(10)) {
            case 0, 1, 2, 3 -> 1;
            case 4, 5, 6, 7 -> 2;
            case 8 -> 3;
            default -> 4;
        };
        List<String> pool = new ArrayList<>(free);
        pool.sort((a, b) -> a.charAt(0) != b.charAt(0)
                ? Character.compare(a.charAt(0), b.charAt(0))
                : Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1))));
        if (random.nextBoolean()) {
            // a contiguous run in the same row, the way a family books
            for (int tries = 0; tries < 12; tries++) {
                String start = pool.get(random.nextInt(pool.size()));
                char row = start.charAt(0);
                int number = Integer.parseInt(start.substring(1));
                Set<String> run = new HashSet<>();
                for (int i = 0; i < howMany; i++) {
                    String code = row + String.valueOf(number + i);
                    if (!free.contains(code)) {
                        break;
                    }
                    run.add(code);
                }
                if (run.size() == howMany) {
                    return run;
                }
                if (howMany == 1 && !run.isEmpty()) {
                    return run;
                }
            }
        }
        Set<String> picked = new HashSet<>();
        List<String> remaining = new ArrayList<>(free);
        for (int i = 0; i < howMany && !remaining.isEmpty(); i++) {
            picked.add(remaining.remove(random.nextInt(remaining.size())));
        }
        return picked;
    }

    /** Most tickets are bought in the last three days before the show; a few weeks in advance. */
    private LocalDateTime paidAtFor(Showtime show, Random random) {
        int hoursBefore = switch (random.nextInt(10)) {
            case 0, 1, 2 -> 1 + random.nextInt(6);
            case 3, 4, 5, 6 -> 12 + random.nextInt(24);
            case 7, 8 -> 36 + random.nextInt(48);
            default -> 96 + random.nextInt(120);
        };
        LocalDateTime paidAt = show.getStartTime().minusHours(hoursBefore).minusMinutes(random.nextInt(60));
        return paidAt.isAfter(LocalDateTime.now()) ? LocalDateTime.now().minusHours(2) : paidAt;
    }

    /** People arrive between 35 and 2 minutes before the film starts. */
    private LocalDateTime scanTimeFor(Showtime show, Random random) {
        return show.getStartTime().minusMinutes(2 + random.nextInt(34));
    }

    private Payment successfulPayment(Booking booking, LocalDateTime paidAt, Random random) {
        PaymentMethodType method = switch (random.nextInt(10)) {
            case 0, 1, 2, 3, 4, 5 -> PaymentMethodType.CARD;
            case 6, 7 -> PaymentMethodType.WALLET;
            default -> PaymentMethodType.CASH_COUNTER;
        };
        BigDecimal fee = method == PaymentMethodType.CARD
                ? booking.getTotalAmount().multiply(CARD_FEE_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new Payment("SEED-" + booking.getReference(), booking,
                booking.getTotalAmount().add(fee), fee, method, PaymentStatus.SUCCESS,
                method == PaymentMethodType.CARD ? "**** " + (1000 + random.nextInt(9000)) : "seed-ref",
                paidAt);
    }

    private Payment refundedPayment(Booking booking, LocalDateTime paidAt, Random random) {
        Payment payment = successfulPayment(booking, paidAt, random);
        payment.refund(booking.getTotalAmount(), booking.getTotalAmount());
        return payment;
    }

    private String reference(int index, Random random) {
        return "H" + Integer.toString(index, 36).toUpperCase() + String.format("%04X", random.nextInt(0xFFFF));
    }
}
