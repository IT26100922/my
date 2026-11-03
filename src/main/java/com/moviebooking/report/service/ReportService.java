package com.moviebooking.report.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.report.dto.ReportDto.DayPoint;
import com.moviebooking.report.dto.ReportDto.MethodRow;
import com.moviebooking.report.dto.ReportDto.MovieRow;
import com.moviebooking.report.dto.ReportDto.SalesReport;
import com.moviebooking.report.dto.ReportDto.Totals;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * READ-only sales analytics for the admin dashboard.
 * Revenue is counted on the day the payment was taken; refunds are counted on the same payment day.
 * Tickets are the seats of confirmed bookings. Occupancy is booked seats / seats of the shows that start in the period.
 * Payments on cancelled bookings are left out of every money, ticket, chart and movie figure. They stay in the
 * payment history (the Payments list), but do not count as sales or refunds here.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final int DEFAULT_DAYS = 14;
    private static final int MAX_DAYS = 366;

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final ShowtimeRepository showtimes;

    public ReportService(PaymentRepository payments, BookingRepository bookings, ShowtimeRepository showtimes) {
        this.payments = payments;
        this.bookings = bookings;
        this.showtimes = showtimes;
    }

    /** Builds the sales report. Missing dates default to the last 14 days. */
    public SalesReport sales(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException("The start date must be on or before the end date");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new BusinessException("Choose a period of " + MAX_DAYS + " days or less");
        }
        LocalDateTime from0 = start.atStartOfDay();
        LocalDateTime to0 = end.plusDays(1).atStartOfDay();

        List<Payment> paid = payments.findAll().stream()
                .filter(p -> p.getBooking().getStatus() != BookingStatus.CANCELLED)
                .filter(p -> !p.getPaidAt().isBefore(from0) && p.getPaidAt().isBefore(to0)).toList();

        BigDecimal gross = BigDecimal.ZERO, refunds = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        long tickets = 0;
        Map<LocalDate, long[]> dayTickets = new TreeMap<>();
        Map<LocalDate, BigDecimal> dayNet = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dayNet.put(d, BigDecimal.ZERO);
            dayTickets.put(d, new long[]{0});
        }
        Map<String, BigDecimal[]> movieNet = new LinkedHashMap<>();
        Map<String, long[]> movieTickets = new LinkedHashMap<>();
        Map<String, long[]> methodCount = new TreeMap<>();
        Map<String, BigDecimal> methodAmount = new TreeMap<>();

        for (Payment p : paid) {
            BigDecimal net = p.getAmount().subtract(p.getRefundedAmount());
            gross = gross.add(p.getAmount());
            refunds = refunds.add(p.getRefundedAmount());
            fees = fees.add(p.getConvenienceFee());

            Booking b = p.getBooking();
            long seats = b.getStatus() == BookingStatus.CONFIRMED ? b.getSeatCount() : 0;
            tickets += seats;

            LocalDate day = p.getPaidAt().toLocalDate();
            dayNet.merge(day, net, BigDecimal::add);
            dayTickets.computeIfAbsent(day, k -> new long[]{0})[0] += seats;

            String title = b.getShow().getMovie().getTitle();
            movieNet.computeIfAbsent(title, k -> new BigDecimal[]{BigDecimal.ZERO})[0] =
                    movieNet.get(title)[0].add(net);
            movieTickets.computeIfAbsent(title, k -> new long[]{0})[0] += seats;

            String method = p.getMethod().name();
            methodCount.computeIfAbsent(method, k -> new long[]{0})[0]++;
            methodAmount.merge(method, p.getAmount(), BigDecimal::add);
        }

        BigDecimal net = gross.subtract(refunds);

        List<Booking> periodBookings = bookings.findAll().stream()
                .filter(b -> !b.getCreatedAt().isBefore(from0) && b.getCreatedAt().isBefore(to0)).toList();
        long confirmed = periodBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long cancelled = periodBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        List<Showtime> shows = showtimes.findAll().stream()
                .filter(s -> !s.getStartTime().isBefore(from0) && s.getStartTime().isBefore(to0)).toList();
        long capacity = shows.stream().mapToLong(s -> s.getScreen().getTotalSeats()).sum();
        Set<Long> showIds = shows.stream().map(Showtime::getId).collect(Collectors.toSet());
        long booked = bookings.findAll().stream()
                .filter(b -> BookingStatus.activeStatuses().contains(b.getStatus()))
                .filter(b -> showIds.contains(b.getShow().getId()))
                .mapToLong(Booking::getSeatCount).sum();
        double occupancy = capacity == 0 ? 0 : BigDecimal.valueOf(booked * 100.0 / capacity)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();

        Totals totals = new Totals(scale(gross), scale(refunds), scale(net), scale(fees), paid.size(), tickets,
                confirmed, cancelled, shows.size(), occupancy);

        List<DayPoint> daily = dayNet.entrySet().stream()
                .map(e -> new DayPoint(e.getKey().toString(), scale(e.getValue()),
                        dayTickets.get(e.getKey())[0]))
                .toList();

        List<MovieRow> byMovie = movieNet.entrySet().stream()
                .map(e -> new MovieRow(e.getKey(), scale(e.getValue()[0]), movieTickets.get(e.getKey())[0]))
                .sorted(Comparator.comparing(MovieRow::net).reversed())
                .limit(6).toList();

        List<MethodRow> byMethod = methodCount.entrySet().stream()
                .map(e -> new MethodRow(e.getKey(), e.getValue()[0], scale(methodAmount.get(e.getKey()))))
                .toList();

        return new SalesReport(start, end, totals, daily, byMovie, byMethod);
    }

    /** Daily rows as CSV text for spreadsheets. */
    public String salesCsv(LocalDate from, LocalDate to) {
        SalesReport r = sales(from, to);
        Totals t = r.totals();
        StringBuilder sb = new StringBuilder();

        // a byte-order mark so Excel on Windows reads the block characters as UTF-8
        sb.append('\uFEFF');
        sb.append("# CineMax sales report\n");
        sb.append("# period,").append(r.from()).append(',').append(r.to()).append('\n');
        sb.append("# currency,LKR\n");
        sb.append("# note,payments on cancelled bookings are excluded from every money figure below\n");
        sb.append("# graphics,columns called *bar* draw a chart inside the cell - set that column to a monospace font ")
                .append("(Consolas or Courier New) and the bars line up\n");
        sb.append("\n");

        sb.append("SUMMARY\n");
        sb.append("metric,value\n");
        sb.append("gross_sales_lkr,").append(q(t.gross())).append('\n');
        sb.append("refunds_lkr,").append(q(t.refunds())).append('\n');
        sb.append("net_sales_lkr,").append(q(t.net())).append('\n');
        sb.append("convenience_fees_lkr,").append(q(t.fees())).append('\n');
        sb.append("payments,").append(t.payments()).append('\n');
        sb.append("tickets_sold,").append(t.tickets()).append('\n');
        sb.append("confirmed_bookings,").append(t.confirmedBookings()).append('\n');
        sb.append("cancelled_bookings,").append(t.cancelledBookings()).append('\n');
        sb.append("showtimes,").append(t.showCount()).append('\n');
        sb.append("occupancy_percent,").append(String.format(Locale.ROOT, "%.1f", t.occupancyPercent())).append('\n');
        // one cell that draws the whole period as a sparkline
        sb.append("daily_net_sparkline,").append(sparkline(r.daily())).append('\n');
        sb.append("\n");

        // ---- the figures, each row drawn with an in-cell bar -------------------
        sb.append("DAILY\n");
        sb.append("date,net_revenue_lkr,tickets,bar\n");
        BigDecimal dayMax = maxOf(r.daily().stream().map(DayPoint::net).toList());
        for (DayPoint d : r.daily()) {
            sb.append(d.date()).append(',').append(q(d.net())).append(',').append(d.tickets()).append(',')
                    .append(csv(bar(d.net(), dayMax, 28))).append('\n');
        }
        sb.append("\n");

        sb.append("BY_MOVIE\n");
        sb.append("title,tickets,net_lkr,share_percent,bar\n");
        BigDecimal movieTotal = sumOf(r.byMovie().stream().map(MovieRow::net).toList());
        BigDecimal movieMax = maxOf(r.byMovie().stream().map(MovieRow::net).toList());
        for (MovieRow m : r.byMovie()) {
            sb.append(csv(m.title())).append(',').append(m.tickets()).append(',').append(q(m.net())).append(',')
                    .append(pct(m.net(), movieTotal)).append(',').append(csv(bar(m.net(), movieMax, 34))).append('\n');
        }
        sb.append("\n");

        sb.append("BY_PAYMENT_METHOD\n");
        sb.append("method,payments,amount_lkr,share_percent,bar\n");
        BigDecimal methodTotal = sumOf(r.byMethod().stream().map(MethodRow::amount).toList());
        BigDecimal methodMax = maxOf(r.byMethod().stream().map(MethodRow::amount).toList());
        for (MethodRow m : r.byMethod()) {
            sb.append(m.method()).append(',').append(m.count()).append(',').append(q(m.amount())).append(',')
                    .append(pct(m.amount(), methodTotal)).append(',').append(csv(bar(m.amount(), methodMax, 34))).append('\n');
        }
        return sb.toString();
    }

    /** Eighth-blocks, so a bar grows in small steps instead of jumping a whole character at a time. */
    private static final char[] BLOCKS = {' ', '\u258F', '\u258E', '\u258D', '\u258C', '\u258B', '\u258A', '\u2589', '\u2588'};

    /**
     * Draws a value as a bar made of block characters, at most {@code cells} wide.
     * This is what gives the CSV file a real chart without any image support.
     */
    static String bar(BigDecimal value, BigDecimal max, int cells) {
        if (value == null || max == null || max.signum() <= 0 || value.signum() <= 0) return "";
        double ratio = Math.min(1.0, value.doubleValue() / max.doubleValue());
        int eighths = (int) Math.round(ratio * cells * 8);
        int full = eighths / 8;
        int part = eighths % 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < full; i++) sb.append(BLOCKS[8]);
        if (part > 0 && full < cells) sb.append(BLOCKS[part]);
        return sb.toString();
    }

    /** One cell that shows the whole period as a sparkline, like the small charts on a dashboard. */
    static String sparkline(List<DayPoint> days) {
        char[] steps = {'\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588'};
        BigDecimal max = maxOf(days.stream().map(DayPoint::net).toList());
        if (max.signum() <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (DayPoint d : days) {
            double ratio = d.net().doubleValue() / max.doubleValue();
            int idx = ratio <= 0 ? 0 : Math.max(0, Math.min(7, (int) Math.ceil(ratio * 8) - 1));
            sb.append(steps[idx]);
        }
        return sb.toString();
    }

    private static BigDecimal maxOf(List<BigDecimal> values) {
        BigDecimal max = BigDecimal.ZERO;
        for (BigDecimal v : values) if (v != null && v.compareTo(max) > 0) max = v;
        return max;
    }

    private static BigDecimal sumOf(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) if (v != null) sum = sum.add(v);
        return sum;
    }

    private static String pct(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0 || value == null) return "0";
        return String.valueOf(Math.round(value.doubleValue() / total.doubleValue() * 100));
    }

    /** Plain 2-decimal number without grouping, so spreadsheets read it as a number. */
    private static String q(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Escapes a CSV field: quotes are doubled, and a field containing , or " is wrapped in quotes. */
    private static String csv(String value) {
        if (value == null) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        if (v.contains(",") || v.contains("\"") || v.contains(";")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
