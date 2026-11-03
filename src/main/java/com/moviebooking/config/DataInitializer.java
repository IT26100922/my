package com.moviebooking.config;

import com.moviebooking.file.service.FileService;
import com.moviebooking.movie.dto.MovieDto.MovieResponse;
import com.moviebooking.promo.entity.PromoCode;
import com.moviebooking.promo.entity.PromoType;
import com.moviebooking.promo.repository.PromoCodeRepository;
import com.moviebooking.movie.service.MovieService;
import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.theatre.dto.ScreenDto.ScreenRequest;
import com.moviebooking.theatre.dto.ScreenDto.ScreenResponse;
import com.moviebooking.theatre.dto.TheatreDto.TheatreRequest;
import com.moviebooking.theatre.dto.TheatreDto.TheatreResponse;
import com.moviebooking.theatre.repository.TheatreRepository;
import com.moviebooking.theatre.service.ScreenService;
import com.moviebooking.theatre.service.TheatreService;
import com.moviebooking.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Loads demo data on the first start (only when the live tables are empty).
 * <p>
 * Users, movies and showtimes are copied out of the MySQL staging tables (import_movies, import_users,
 * import_showtimes) - the same path the Import buttons use. Nothing is read from a text file.
 * Theatres and screens are created in code, because they belong to the cinema layout rather than to
 * import data. If the staging tables are empty too (the SQL setup script was not run), the app still
 * starts and logs exactly what to do.
 */
@Component
@Order(1)   // runs before DemoHistorySeeder, which needs the films, screens and promo codes from here
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FileService fileService;
    private final UserRepository users;
    private final TheatreRepository theatres;
    private final ShowtimeRepository showtimes;
    private final MovieService movieService;
    private final TheatreService theatreService;
    private final ScreenService screenService;
    private final ShowService showService;
    private final PromoCodeRepository promos;
    private final SampleImportSeeder sampleSeeder;

    public DataInitializer(FileService fileService, UserRepository users, TheatreRepository theatres,
                           ShowtimeRepository showtimes, MovieService movieService,
                           TheatreService theatreService, ScreenService screenService, ShowService showService,
                           PromoCodeRepository promos, SampleImportSeeder sampleSeeder) {
        this.promos = promos;
        this.sampleSeeder = sampleSeeder;
        this.fileService = fileService;
        this.users = users;
        this.theatres = theatres;
        this.showtimes = showtimes;
        this.movieService = movieService;
        this.theatreService = theatreService;
        this.screenService = screenService;
        this.showService = showService;
    }

    @Override
    public void run(String... args) {
        // A database that Hibernate created by itself has empty import tables - and then nobody can log
        // in. One call fills them with the built-in sample rows before anything else touches the data.
        sampleSeeder.seedIfEmpty();
        if (users.count() == 0) {
            importSafely("Users", fileService::importUsers);
        }
        if (movieService.search("", "").isEmpty()) {
            importSafely("Movies", fileService::importMovies);
        }
        if (theatres.count() == 0) {
            seedCinemas();
        }
        importSafely("Showtimes", fileService::importShowtimes);
        if (showtimes.count() == 0) {
            seedShowtimes();
        }
        seedPromoCodes();
        reportStartupState();
    }

    /** Two demo promo codes, so the checkout discount can be tried without creating one first. */
    private void seedPromoCodes() {
        if (promos.count() > 0) {
            return;
        }
        promos.save(new PromoCode("WELCOME10", PromoType.PERCENT, new BigDecimal("10"),
                new BigDecimal("1000"), LocalDate.now().minusDays(1), LocalDate.now().plusMonths(6), 500,
                "10% off any booking over LKR 1,000"));
        promos.save(new PromoCode("IMAX500", PromoType.FLAT, new BigDecimal("500"),
                new BigDecimal("3000"), LocalDate.now().minusDays(1), LocalDate.now().plusMonths(3), 200,
                "LKR 500 off bookings over LKR 3,000"));
        log.info("Seeded 2 demo promo codes (WELCOME10, IMAX500)");
    }

    /**
     * An empty staging table must not stop the application from starting: the import reports zero rows,
     * and a clear message explains how to fill the staging tables with SQL.
     */
    private void importSafely(String label, Supplier<?> importAction) {
        try {
            Object result = importAction.get();
            log.info("{} import from MySQL staging: {}", label, result);
        } catch (RuntimeException e) {
            log.error("{} rows could NOT be imported into MySQL: {}. Check the staging table with "
                    + "'SELECT * FROM import_{};' and run database/cinemax_mysql_setup.sql (SECTION 4) if it is empty. "
                    + "The application continues to start.", label, e.getMessage(), label.toLowerCase(), e);
        }
    }

    /** Called once the imports are done: says plainly where the data came from, or what is missing. */
    private void reportStartupState() {
        if (users.count() == 0) {
            log.error("There is no account in the database, so nobody can log in yet. Run "
                    + "database/cinemax_mysql_setup.sql (SECTION 4), then restart the application.");
        } else if (!fileService.hasStagingData()) {
            log.warn("The MySQL import tables import_movies / import_users / import_showtimes are empty. "
                    + "Run database/cinemax_mysql_setup.sql (SECTION 4 fills them), then use "
                    + "Admin -> Files & Reports -> Import movies / users / showtimes.");
        } else {
            log.info("Data source: MySQL staging tables {}", fileService.status());
        }
    }

    private void seedCinemas() {
        TheatreResponse negombo = theatreService.create(new TheatreRequest(
                "CineMax Negombo", "Negombo", "Main Street, Negombo", "+94 31 222 4100"));
        TheatreResponse colombo = theatreService.create(new TheatreRequest(
                "CineMax Colombo", "Colombo", "Galle Road, Colombo 03", "+94 11 223 4500"));

        // Every theatre gets its own screens, and every screen has its own seat layout (rows x seats per row).
        screenService.create(new ScreenRequest("Screen 1 - Standard", negombo.id(), "STANDARD", 8, 12));
        screenService.create(new ScreenRequest("Screen 2 - Premium", negombo.id(), "PREMIUM", 6, 10));
        screenService.create(new ScreenRequest("Screen 3 - VIP", negombo.id(), "VIP", 4, 8));

        screenService.create(new ScreenRequest("Screen 1 - Standard", colombo.id(), "STANDARD", 10, 14));
        screenService.create(new ScreenRequest("Screen 2 - Premium", colombo.id(), "PREMIUM", 6, 12));
        screenService.create(new ScreenRequest("Screen 3 - VIP", colombo.id(), "VIP", 5, 10));
    }

    /** Three days of shows starting tomorrow, spread over three screens and two time slots. */
    private void seedShowtimes() {
        List<MovieResponse> allMovies = movieService.search("", "");
        if (allMovies.isEmpty()) {
            log.warn("No movies in the database, so no sample showtimes were created");
            return;
        }
        LocalTime[] slots = {LocalTime.of(11, 0), LocalTime.of(15, 0)};
        BigDecimal basePrice = new BigDecimal("1500.00");
        int seeded = 0;

        // Showtimes are created for every theatre that has screens, so each screen can be booked.
        for (TheatreResponse theatre : theatreService.list()) {
            List<ScreenResponse> screens = screenService.list(theatre.id());
            if (screens.isEmpty()) {
                log.warn("No screens in {}, so no sample showtimes were created there", theatre.name());
                continue;
            }
            for (int day = 1; day <= 3; day++) {
                LocalDate date = LocalDate.now().plusDays(day);
                for (int j = 0; j < allMovies.size(); j++) {
                    MovieResponse movie = allMovies.get(j);
                    ScreenResponse screen = screens.get(j % screens.size());
                    LocalTime slot = slots[(j / screens.size()) % slots.length];
                    try {
                        showService.create(new ShowRequest(movie.id(), screen.id(),
                                LocalDateTime.of(date, slot), basePrice));
                        seeded++;
                    } catch (RuntimeException ex) {
                        log.warn("Skipped seed showtime for {}: {}", movie.title(), ex.getMessage());
                    }
                }
            }
        }
        log.info("Seeded {} showtimes across all theatres", seeded);
    }
}
