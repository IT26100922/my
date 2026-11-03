package com.moviebooking.file.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.file.dto.FileDto.ImportResult;
import com.moviebooking.file.dto.FileDto.StagingStatus;
import com.moviebooking.file.entity.ImportMovieRow;
import com.moviebooking.file.entity.ImportShowtimeRow;
import com.moviebooking.file.entity.ImportUserRow;
import com.moviebooking.file.repository.ImportMovieRowRepository;
import com.moviebooking.file.repository.ImportShowtimeRowRepository;
import com.moviebooking.file.repository.ImportUserRowRepository;
import com.moviebooking.movie.dto.MovieDto.MovieRequest;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.movie.service.MovieService;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.theatre.entity.Screen;
import com.moviebooking.theatre.repository.ScreenRepository;
import com.moviebooking.theatre.repository.TheatreRepository;
import com.moviebooking.user.dto.AuthDto.RegisterCustomerRequest;
import com.moviebooking.user.dto.UserDto.CreateAdminRequest;
import com.moviebooking.user.repository.UserRepository;
import com.moviebooking.user.service.AdminService;
import com.moviebooking.user.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * IMPORT AND EXPORT - all of it through MySQL.
 * <p>
 * The three import buttons read the staging tables {@code import_movies}, {@code import_users} and
 * {@code import_showtimes} and copy those rows into the live tables. No text file is opened at any
 * point: the data lives in the database, and you can add or edit staging rows with plain SQL, from
 * MySQL Workbench, or from another application.
 * <p>
 * The two "refresh staging" actions run the other way: they copy the live tables back into the
 * staging tables, so the staging area always offers a current snapshot.
 * <p>
 * The only file this class still writes is the timestamped text version of the sales report
 * (data/reports/), which is a download artifact rather than import data.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MovieService movieService;
    private final MovieRepository movies;
    private final CustomerService customerService;
    private final AdminService adminService;
    private final ShowService showService;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ImportMovieRowRepository stagedMovies;
    private final ImportUserRowRepository stagedUsers;
    private final ImportShowtimeRowRepository stagedShowtimes;
    private final ShowtimeRepository showtimes;
    private final UserRepository users;
    private final TheatreRepository theatres;
    private final ScreenRepository screens;
    private final Path reportsDir;

    public FileService(MovieService movieService, MovieRepository movies, CustomerService customerService, AdminService adminService,
                       ShowService showService, BookingRepository bookings, PaymentRepository payments,
                       ImportMovieRowRepository stagedMovies, ImportUserRowRepository stagedUsers,
                       ImportShowtimeRowRepository stagedShowtimes, ShowtimeRepository showtimes,
                       UserRepository users, TheatreRepository theatres, ScreenRepository screens,
                       @Value("${app.data.reports-dir}") String reportsDir) {
        this.movieService = movieService;
        this.movies = movies;
        this.customerService = customerService;
        this.adminService = adminService;
        this.showService = showService;
        this.bookings = bookings;
        this.payments = payments;
        this.stagedMovies = stagedMovies;
        this.stagedUsers = stagedUsers;
        this.stagedShowtimes = stagedShowtimes;
        this.showtimes = showtimes;
        this.users = users;
        this.theatres = theatres;
        this.screens = screens;
        this.reportsDir = Paths.get(reportsDir);
    }

    // ------------------------------------------------------------------ counts shown in the UI

    /** How many rows sit in each staging table, and how many are already live. */
    @Transactional(readOnly = true)
    public StagingStatus status() {
        return new StagingStatus(
                "MySQL",
                stagedMovies.count(), movies.count(),
                stagedUsers.count(), users.count(),
                stagedShowtimes.count(), showtimes.count());
    }

    // ------------------------------------------------------------------ import: staging table -> live table

    /**
     * Copies every new row of import_movies into the movies table. Titles that already exist are skipped.
     * <p>
     * No @Transactional here on purpose: each row is saved by its own service call, so one bad row
     * cannot roll back the rows that were already imported, and a "duplicate" is a normal skip
     * instead of an error for the whole batch.
     */
    public ImportResult importMovies() {
        List<ImportMovieRow> rows = stagedMovies.findAllByOrderByIdAsc();
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (ImportMovieRow row : rows) {
            try {
                if (row.getTitle() == null || row.getTitle().isBlank()) {
                    throw new IllegalArgumentException("title is empty in import_movies id " + row.getId());
                }
                if (movieService.titleExists(row.getTitle())) {
                    skipped++;
                    continue;
                }
                if (row.getDurationMinutes() == null) {
                    throw new IllegalArgumentException("duration_minutes is empty for \"" + row.getTitle() + "\"");
                }
                String format = row.getMovieFormat() == null || row.getMovieFormat().isBlank()
                        ? "STANDARD" : row.getMovieFormat().trim();
                movieService.create(new MovieRequest(
                        row.getTitle().trim(),
                        value(row.getGenre(), "Drama"),
                        value(row.getLanguage(), "English"),
                        row.getDurationMinutes(),
                        row.getRating(),
                        row.getDescription(),
                        null,
                        row.getReleaseDate(),
                        format,
                        row.getDirector(),
                        row.getProducers(),
                        row.getWriters(),
                        row.getCastMembers(),
                        row.getTrailerUrl(),
                        row.getStillUrls()));
                imported++;
            } catch (RuntimeException ex) {
                errors.add("import_movies id " + row.getId() + ": " + ex.getMessage());
            }
        }
        log.info("Movie import from MySQL: {} imported, {} skipped, {} error(s)", imported, skipped, errors.size());
        return new ImportResult("MySQL table import_movies", rows.size(), imported, skipped, errors);
    }

    /** Copies every new row of import_users into the users table. Emails that already exist are skipped. */
    public ImportResult importUsers() {
        List<ImportUserRow> rows = stagedUsers.findAllByOrderByIdAsc();
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (ImportUserRow row : rows) {
            try {
                if (row.getEmail() == null || row.getEmail().isBlank()) {
                    throw new IllegalArgumentException("email is empty in import_users id " + row.getId());
                }
                String type = row.getAccountType() == null ? "" : row.getAccountType().trim().toUpperCase();
                String password = row.getPasswordHash() == null ? "" : row.getPasswordHash().trim();
                // A BCrypt hash is inserted as it is; anything else is treated as a plain password and hashed.
                boolean hashed = password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
                if ("CUSTOMER".equals(type)) {
                    if (hashed) {
                        customerService.importCustomer(row.getFullName(), row.getEmail(), password, row.getPhone());
                    } else {
                        customerService.register(new RegisterCustomerRequest(
                                row.getFullName(), row.getEmail(), password, row.getPhone()));
                    }
                } else if ("ADMIN".equals(type)) {
                    if (hashed) {
                        adminService.importAdmin(row.getFullName(), row.getEmail(), password, row.getPermissionLevel());
                    } else {
                        adminService.createAdmin(null, new CreateAdminRequest(
                                row.getFullName(), row.getEmail(), password, row.getPermissionLevel()));
                    }
                } else {
                    throw new IllegalArgumentException("account_type must be ADMIN or CUSTOMER, found \"" + type + "\"");
                }
                imported++;
            } catch (BusinessException ex) {
                skipped++; // usually "email already exists"
            } catch (RuntimeException ex) {
                errors.add("import_users id " + row.getId() + ": " + ex.getMessage());
            }
        }
        log.info("User import from MySQL: {} imported, {} skipped, {} error(s)", imported, skipped, errors.size());
        return new ImportResult("MySQL table import_users", rows.size(), imported, skipped, errors);
    }

    /** Copies every upcoming row of import_showtimes into the showtimes table. Past and duplicate shows are skipped. */
    public ImportResult importShowtimes() {
        List<ImportShowtimeRow> rows = stagedShowtimes.findAllByOrderByIdAsc();
        LocalDateTime now = LocalDateTime.now();
        long dayShift = dayShiftToFuture(rows.stream().map(ImportShowtimeRow::getStartTime).toList(), now);
        if (dayShift > 0) {
            log.info("Showtimes in import_showtimes were in the past; moved all of them {} day(s) later", dayShift);
        }
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (ImportShowtimeRow row : rows) {
            try {
                if (row.getStartTime() == null) {
                    throw new IllegalArgumentException("start_time is empty in import_showtimes id " + row.getId());
                }
                LocalDateTime start = row.getStartTime().plusDays(dayShift);
                if (start.isBefore(now)) {
                    skipped++;
                    continue;
                }
                Movie movie = showService.findMovieByTitle(row.getMovieTitle())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no movie titled \"" + row.getMovieTitle() + "\" in the movies table"));
                Screen screen = showService.findScreenByNames(row.getTheatreName(), row.getScreenName())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no screen \"" + row.getScreenName() + "\" in theatre \"" + row.getTheatreName() + "\""));
                if (showService.existsAt(screen.getId(), start)) {
                    skipped++;
                    continue;
                }
                BigDecimal price = row.getBasePrice() == null ? new BigDecimal("1500.00") : row.getBasePrice();
                showService.create(new ShowRequest(movie.getId(), screen.getId(), start, price));
                imported++;
            } catch (RuntimeException ex) {
                errors.add("import_showtimes id " + row.getId() + ": " + ex.getMessage());
            }
        }
        log.info("Showtime import from MySQL: {} imported, {} skipped, {} error(s)", imported, skipped, errors.size());
        return new ImportResult("MySQL table import_showtimes", rows.size(), imported, skipped, errors);
    }

    /**
     * Staging rows keep fixed dates, so on an old database they can all be in the past.
     * Returns the whole number of days to add to every staged show so the earliest one is in the future,
     * so the timetable keeps its shape. Returns 0 when no move is needed.
     */
    static long dayShiftToFuture(List<LocalDateTime> starts, LocalDateTime now) {
        LocalDateTime earliest = null;
        for (LocalDateTime start : starts) {
            if (start != null && (earliest == null || start.isBefore(earliest))) {
                earliest = start;
            }
        }
        if (earliest == null || !earliest.isBefore(now)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(earliest.toLocalDate(), now.toLocalDate());
        if (!earliest.plusDays(days).isAfter(now)) {
            days++;
        }
        return days;
    }

    // ------------------------------------------------------------------ refresh staging: live table -> staging table

    /**
     * Rewrites import_movies from the movies table, so the staging area always holds a current snapshot.
     * Called after a movie is added, edited or deleted in the admin portal. Nothing touches the disk.
     */
    @Transactional
    public int exportMovies() {
        List<Movie> live = movieService.allForExport();
        stagedMovies.deleteAllInBatch();
        List<ImportMovieRow> rows = new ArrayList<>();
        for (Movie m : live) {
            ImportMovieRow row = new ImportMovieRow();
            row.setFrom(m.getTitle(), m.getGenre(), m.getLanguage(), m.getDurationMinutes(), m.getRating(),
                    m.getFormat(), m.getReleaseDate(), m.getDirector(), m.getProducers(), m.getWriters(),
                    m.getCastMembers(), m.getTrailerUrl(), m.getStillUrls(), m.getDescription());
            rows.add(row);
        }
        stagedMovies.saveAll(rows);
        log.info("import_movies refreshed from the movies table: {} row(s)", rows.size());
        return rows.size();
    }

    /**
     * Rewrites import_showtimes from the upcoming showtimes in the showtimes table.
     * Called after a showtime is added, edited or deleted in the admin portal.
     */
    @Transactional
    public int exportShowtimes() {
        List<Showtime> live = showService.upcomingForExport();
        stagedShowtimes.deleteAllInBatch();
        List<ImportShowtimeRow> rows = new ArrayList<>();
        for (Showtime s : live) {
            ImportShowtimeRow row = new ImportShowtimeRow();
            row.setFrom(s.getMovie().getTitle(), s.getScreen().getTheatre().getName(), s.getScreen().getName(),
                    s.getStartTime(), s.getBasePrice());
            rows.add(row);
        }
        stagedShowtimes.saveAll(rows);
        log.info("import_showtimes refreshed from the showtimes table: {} row(s)", rows.size());
        return rows.size();
    }

    // ------------------------------------------------------------------ the timestamped text report

    /** WRITE a sales report to data/reports/sales-report-<timestamp>.txt and return its path. */
    public Path exportSalesReport() {
        List<Booking> allBookings = bookings.findAllByOrderByCreatedAtDesc();
        List<Payment> allPayments = payments.findAllByOrderByPaidAtDesc();

        // Payments on cancelled bookings are not sales: they are left out of the money totals.
        List<Payment> salePayments = allPayments.stream()
                .filter(p -> p.getBooking().getStatus() != BookingStatus.CANCELLED).toList();
        BigDecimal gross = salePayments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = salePayments.stream().map(Payment::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
        long confirmed = countStatus(allBookings, BookingStatus.CONFIRMED);
        long pending = countStatus(allBookings, BookingStatus.PENDING_PAYMENT);
        long cancelled = countStatus(allBookings, BookingStatus.CANCELLED);

        Map<String, Long> seatsPerMovie = allBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getShow().getMovie().getTitle(), TreeMap::new,
                        Collectors.summingLong(Booking::getSeatCount)));

        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(96);
        sb.append(line).append('\n');
        sb.append("MOVIE TICKET BOOKING - SALES REPORT\n");
        sb.append("Generated: ").append(LocalDateTime.now()).append('\n');
        sb.append(line).append('\n');
        sb.append(String.format("Bookings  : %d total | %d confirmed | %d awaiting payment | %d cancelled%n",
                allBookings.size(), confirmed, pending, cancelled));
        sb.append(String.format("Payments  : %d (excluding payments on cancelled bookings)%n", salePayments.size()));
        sb.append(String.format("Gross     : LKR %,.2f%n", gross));
        sb.append(String.format("Refunds   : LKR %,.2f%n", refunds));
        sb.append(String.format("Net sales : LKR %,.2f%n", net));
        sb.append('\n').append("Seats sold per movie (excluding cancelled):\n");
        seatsPerMovie.forEach((title, seats) -> sb.append(String.format("  %-30s %6d%n", title, seats)));

        sb.append('\n').append("Booking details:\n");
        sb.append(String.format("%-10s %-28s %-16s %-17s %6s %14s  %-22s%n",
                "REF", "MOVIE", "CUSTOMER", "SHOW TIME", "SEATS", "AMOUNT", "STATUS"));
        for (Booking b : allBookings) {
            sb.append(String.format("%-10s %-28s %-16s %-17s %6d %14s  %-22s%n",
                    b.getReference(),
                    trim(b.getShow().getMovie().getTitle(), 28),
                    trim(b.getCustomer().getFullName(), 16),
                    b.getShow().getStartTime().toLocalDate() + " " + b.getShow().getStartTime().toLocalTime().withSecond(0).toString(),
                    b.getSeatCount(),
                    String.format("LKR %,.2f", b.getTotalAmount()),
                    b.getStatus().name()));
        }
        sb.append(line).append('\n');

        try {
            Files.createDirectories(reportsDir);
            Path file = reportsDir.resolve("sales-report-" + LocalDateTime.now().format(STAMP) + ".txt");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report", e);
        }
    }

    // ------------------------------------------------------------------ small helpers

    /** True when the staging tables hold rows but the live tables are still empty (used at start-up). */
    @Transactional(readOnly = true)
    public boolean hasStagingData() {
        return stagedMovies.count() > 0 || stagedUsers.count() > 0 || stagedShowtimes.count() > 0;
    }

    /** Counts of the live tables, for the dashboard cards. */
    @Transactional(readOnly = true)
    public Map<String, Long> liveCounts() {
        return Map.of("movies", movies.count(), "users", users.count(),
                "showtimes", showtimes.count(), "theatres", theatres.count(), "screens", screens.count());
    }

    private static String value(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    private static long countStatus(List<Booking> list, BookingStatus status) {
        return list.stream().filter(b -> b.getStatus() == status).count();
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "~";
    }
}
