package com.moviebooking.analytics.service;

import com.moviebooking.analytics.dto.AnalyticsDto.AnalyticsResponse;
import com.moviebooking.analytics.dto.AnalyticsDto.Funnel;
import com.moviebooking.analytics.dto.AnalyticsDto.Slice;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.show.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Analytics for the admin dashboard: where the money comes from, when the seats sell, and how many of the
 * people who held a seat actually paid and turned up.
 *
 * <p>Everything is counted from the tables rather than from a cached summary, so the page is correct right
 * after a booking, a refund or a scan. Money is counted on the day the payment was taken, and payments on
 * cancelled bookings are left out — the same rule the sales report uses, so the figures always agree.</p>
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final ShowtimeRepository showtimes;

    public AnalyticsService(PaymentRepository payments, BookingRepository bookings, ShowtimeRepository showtimes) {
        this.payments = payments;
        this.bookings = bookings;
        this.showtimes = showtimes;
    }

    /** Builds the whole page for the last {@code days} days (default 7, at most 365). */
    public AnalyticsResponse build(int days) {
        int window = Math.min(365, Math.max(1, days));
        LocalDateTime since = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        List<BookingStatus> paid = BookingStatus.paidStatuses();
        BigDecimal revenue = BigDecimal.ZERO;
        long tickets = 0;
        List<Slice> byMovie = new ArrayList<>();
        for (Object[] row : payments.revenueByMovie(paid, since)) {
            BigDecimal net = (BigDecimal) row[1];
            revenue = revenue.add(net);
            tickets += row[3] == null ? 0 : ((Number) row[3]).longValue();
            byMovie.add(new Slice((String) row[0], scale(net), ((Number) row[2]).longValue()));
        }

        List<Slice> byScreen = payments.revenueByScreen(paid, since).stream()
                .map(r -> new Slice((String) r[0], scale((BigDecimal) r[1]), ((Number) r[2]).longValue()))
                .toList();

        List<Slice> byDay = payments.revenueByDay(paid, since).stream()
                .map(r -> new Slice(String.valueOf(r[0]), scale((BigDecimal) r[1]), 0))
                .toList();

        List<Slice> byHour = bookings.seatsByStartHour(paid, since).stream()
                .map(r -> new Slice(String.format("%02d:00", ((Number) r[0]).intValue()),
                        BigDecimal.valueOf(((Number) r[1]).longValue()), ((Number) r[1]).longValue()))
                .toList();

        List<Slice> methods = payments.methodMix(since).stream()
                .map(r -> new Slice((String) ((Enum<?>) r[0]).name(), scale((BigDecimal) r[2]),
                        ((Number) r[1]).longValue()))
                .toList();

        long paidBookings = bookings.countByStatusSince(paid, since);
        long holds = bookings.countCreatedSince(since);
        long cancelled = bookings.countByStatusSince(List.of(BookingStatus.CANCELLED), since);
        long checkedIn = bookings.findCheckedInSince(since).size();
        double conversion = holds == 0 ? 0 : round1(paidBookings * 100.0 / holds);

        long noShows = bookings.countNoShows(LocalDateTime.now(), since);

        // The aggregate comes back as one row: [seats sold, seats on sale].
        List<Object[]> occupancyRows = showtimes.occupancyForPeriod(paid, since, LocalDateTime.now().plusYears(1));
        Object[] occupancy = occupancyRows.isEmpty() ? new Object[2] : occupancyRows.get(0);
        long seatsSold = occupancy[0] == null ? 0 : ((Number) occupancy[0]).longValue();
        long seatsOnSale = occupancy[1] == null ? 0 : ((Number) occupancy[1]).longValue();
        double occupancyPercent = seatsOnSale == 0 ? 0 : round1(seatsSold * 100.0 / seatsOnSale);

        BigDecimal average = paidBookings == 0 ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(BigDecimal.valueOf(paidBookings), 2, RoundingMode.HALF_UP);

        return new AnalyticsResponse(window, scale(revenue), tickets, paidBookings, average, occupancyPercent,
                noShows, new Funnel(holds, paidBookings, cancelled, checkedIn, conversion),
                byMovie, byScreen, byDay, byHour, methods);
    }

    /** Today's date label, used by the dashboard header. */
    public String today() {
        return LocalDate.now().format(DAY);
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
