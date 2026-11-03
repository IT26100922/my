package com.moviebooking.show.service;

import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.show.dto.ShowDto.ShowResponse;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.pricing.PricingStrategy;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.theatre.entity.Screen;
import com.moviebooking.theatre.repository.ScreenRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CRUD for showtimes plus pricing.
 * CREATE: create()   READ: search() / getById()   UPDATE: update()   DELETE: delete()
 */
@Service
@Transactional
public class ShowService {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2999, 12, 31, 23, 59);

    private final ShowtimeRepository showtimes;
    private final MovieRepository movies;
    private final ScreenRepository screens;
    private final BookingRepository bookings;
    private final List<PricingStrategy> pricingStrategies;

    public ShowService(ShowtimeRepository showtimes, MovieRepository movies, ScreenRepository screens,
                       BookingRepository bookings, List<PricingStrategy> pricingStrategies) {
        this.showtimes = showtimes;
        this.movies = movies;
        this.screens = screens;
        this.bookings = bookings;
        this.pricingStrategies = pricingStrategies;
    }

    /**
     * READ (public): upcoming shows, optionally filtered by movie and by date.
     * Without a date, shows from now onwards are returned.
     */
    @Transactional(readOnly = true)
    public List<ShowResponse> search(Long movieId, LocalDate date) {
        LocalDateTime from = date != null ? date.atStartOfDay() : LocalDateTime.now();
        LocalDateTime to = date != null ? date.plusDays(1).atStartOfDay() : FAR_FUTURE;
        List<Showtime> found = movieId != null
                ? showtimes.findInRangeForMovie(movieId, from, to)
                : showtimes.findInRange(from, to);
        return found.stream().map(s -> toResponse(s, heldSeats(s.getId(), -1L))).toList();
    }

    /** READ (admin): every showtime, including past ones. */
    @Transactional(readOnly = true)
    public List<ShowResponse> listAll() {
        return showtimes.findAll(Sort.by("startTime")).stream()
                .map(s -> toResponse(s, heldSeats(s.getId(), -1L))).toList();
    }

    /** READ: one showtime with its booked seats. */
    @Transactional(readOnly = true)
    public ShowResponse getById(Long id) {
        Showtime show = find(id);
        return toResponse(show, heldSeats(id, -1L));
    }

    /** CREATE: validates the time, then checks the screen is free. */
    public ShowResponse create(ShowRequest req) {
        Movie movie = findMovie(req.movieId());
        Screen screen = findScreen(req.screenId());
        validateSchedule(req.startTime(), movie, screen, null);
        Showtime show = new Showtime(movie, screen, req.startTime(), req.basePrice());
        return toResponse(showtimes.save(show), List.of());
    }

    /** UPDATE: same validation as create, ignoring the showtime itself. */
    public ShowResponse update(Long id, ShowRequest req) {
        Showtime show = find(id);
        Movie movie = findMovie(req.movieId());
        Screen screen = findScreen(req.screenId());
        validateSchedule(req.startTime(), movie, screen, id);
        show.updateDetails(movie, screen, req.startTime(), req.basePrice());
        return toResponse(show, heldSeats(id, -1L));
    }

    /** DELETE: blocked once customers have booked seats for this showtime. */
    public void delete(Long id) {
        Showtime show = find(id);
        if (bookings.existsByShowId(id)) {
            throw new BusinessException("This showtime has bookings and cannot be deleted");
        }
        showtimes.delete(show);
    }

    /** READ for the seed file: upcoming showtimes only, in time order. */
    @Transactional(readOnly = true)
    public List<Showtime> upcomingForExport() {
        LocalDateTime now = LocalDateTime.now();
        return showtimes.findAll(Sort.by("startTime")).stream()
                .filter(s -> !s.getStartTime().isBefore(now)).toList();
    }

    /** Used by the seed importer: true if a showtime already exists on this screen at this time. */
    @Transactional(readOnly = true)
    public boolean existsAt(Long screenId, LocalDateTime start) {
        return showtimes.findAll().stream()
                .anyMatch(s -> s.getScreen().getId().equals(screenId) && s.getStartTime().equals(start));
    }

    /** Used by the seed importer: finds a movie by its title (ignoring case). */
    @Transactional(readOnly = true)
    public Optional<Movie> findMovieByTitle(String title) {
        return movies.findAll().stream().filter(m -> m.getTitle().equalsIgnoreCase(title.trim())).findFirst();
    }

    /** Used by the seed importer: finds a screen by its theatre name and screen name (ignoring case). */
    @Transactional(readOnly = true)
    public Optional<Screen> findScreenByNames(String theatreName, String screenName) {
        return screens.findAll().stream()
                .filter(s -> s.getTheatre().getName().equalsIgnoreCase(theatreName.trim())
                        && s.getName().equalsIgnoreCase(screenName.trim()))
                .findFirst();
    }

    /**
     * Price per seat for a showtime:
     * (base x screen multiplier + movie surcharge), then the time-based pricing strategy.
     * Used by the booking module when it calculates totals.
     */
    @Transactional(readOnly = true)
    public BigDecimal seatPriceFor(Showtime show) {
        BigDecimal base = show.getBasePrice()
                .multiply(show.getScreen().getPriceMultiplier())
                .add(show.getMovie().getSurcharge());
        return resolveStrategy(show.getStartTime()).priceFor(base, show.getStartTime())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Price of one named seat: the showtime price for a REGULAR row, multiplied by the row's band.
     * The back rows of a screen cost more, so the front rows stay at the strategy price.
     */
    public BigDecimal seatPriceFor(Showtime show, String seatCode) {
        BigDecimal base = seatPriceFor(show);
        return base.multiply(show.getScreen().bandForSeat(seatCode).getMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Total for a set of seats, each priced by the band of its row. */
    public BigDecimal totalForSeats(Showtime show, java.util.Collection<String> seatCodes) {
        return seatCodes.stream()
                .map(code -> seatPriceFor(show, code))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Picks the matching pricing rule with the highest priority. */
    public PricingStrategy resolveStrategy(LocalDateTime showTime) {
        return pricingStrategies.stream()
                .filter(p -> p.supports(showTime))
                .max(Comparator.comparingInt(PricingStrategy::priority))
                .orElseThrow(() -> new IllegalStateException("No pricing strategy available"));
    }

    private void validateSchedule(LocalDateTime start, Movie movie, Screen screen, Long excludeId) {
        if (start.isBefore(LocalDateTime.now())) {
            throw new BusinessException("Showtime must be in the future");
        }
        LocalDateTime end = start.plusMinutes(movie.getDurationMinutes());
        long clashes = showtimes.countOverlapping(screen.getId(), start, end,
                excludeId == null ? -1L : excludeId);
        if (clashes > 0) {
            throw new BusinessException("Screen \"" + screen.getName()
                    + "\" already has a show that overlaps this time");
        }
    }

    private List<String> heldSeats(Long showId, Long excludeBookingId) {
        return bookings.findHeldSeatCodes(showId, excludeBookingId, BookingStatus.activeStatuses(), LocalDateTime.now())
                .stream().sorted().toList();
    }

    private ShowResponse toResponse(Showtime show, List<String> booked) {
        PricingStrategy strategy = resolveStrategy(show.getStartTime());
        return ShowResponse.from(show, seatPriceFor(show), strategy.name(), booked);
    }

    private Showtime find(Long id) {
        return showtimes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + id));
    }

    private Movie findMovie(Long id) {
        return movies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + id));
    }

    private Screen findScreen(Long id) {
        return screens.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }
}
