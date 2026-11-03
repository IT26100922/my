package com.moviebooking.checkin.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.checkin.dto.CheckInDto.CheckInSummary;
import com.moviebooking.checkin.dto.CheckInDto.ScanResult;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket check-in at the door.
 *
 * <p>A cinema's job does not end when the ticket is sold: someone has to let people in and count who came.
 * One scan answers with one clear verdict, so the person at the door never has to interpret:</p>
 * <ul>
 *   <li>{@code OK} — valid ticket for today, now marked as used;</li>
 *   <li>{@code ALREADY_USED} — scanned before (with the time of the first scan);</li>
 *   <li>{@code CANCELLED}, {@code NOT_PAID}, {@code WRONG_DAY}, {@code NOT_STARTED_TODAY}, {@code NOT_FOUND}.</li>
 * </ul>
 */
@Service
@Transactional
public class CheckInService {

    private final BookingRepository bookings;
    private final PaymentRepository payments;

    public CheckInService(BookingRepository bookings, PaymentRepository payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    /** UPDATE: scan a ticket. The booking becomes CHECKED_IN, once. */
    public ScanResult scan(String rawCode, String staff) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            return new ScanResult("NOT_FOUND", "Type or scan a ticket code", null, null, null, null,
                    null, List.of(), null, null, null);
        }
        // The ticket PDF carries the booking reference, so a scanner may also send the whole "BK..." text.
        Booking booking = bookings.findByReference(code).orElse(null);
        if (booking == null) {
            return new ScanResult("NOT_FOUND", "No booking with reference " + code, code, null, null, null,
                    null, List.of(), null, null, null);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return result("CANCELLED", "This booking was cancelled", booking);
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            return result("NOT_PAID", "This ticket was never paid for", booking);
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            return result("ALREADY_USED", "Already scanned at "
                    + booking.getCheckedInAt().toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES), booking);
        }
        LocalDateTime start = booking.getShow().getStartTime();
        LocalDate today = LocalDate.now();
        if (!start.toLocalDate().equals(today)) {
            return result(start.toLocalDate().isBefore(today) ? "WRONG_DAY" : "NOT_STARTED_TODAY",
                    start.toLocalDate().isBefore(today)
                            ? "This ticket was for " + start.toLocalDate()
                            : "This ticket is for " + start.toLocalDate(), booking);
        }
        try {
            booking.checkIn(staff == null || staff.isBlank() ? "CINEMA-DOOR" : staff);
        } catch (IllegalStateException e) {
            return result("ALREADY_USED", e.getMessage(), booking);
        }
        return result("OK", "Welcome in - enjoy the film", booking);
    }

    /** READ: today's attendance, the no-shows so far and the last few scans. */
    @Transactional(readOnly = true)
    public CheckInSummary today() {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Booking> paidToday = bookings.findPaidForDay(BookingStatus.paidStatuses(), from, to);
        long expected = paidToday.size();
        long checkedIn = paidToday.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).count();
        long noShows = paidToday.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> b.getShow().getStartTime().isBefore(LocalDateTime.now()))
                .count();
        double attendance = expected == 0 ? 0 : BigDecimal.valueOf(checkedIn * 100.0 / expected)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        List<ScanResult> recent = bookings.findCheckedInSince(from).stream().limit(8)
                .map(b -> result("OK", "Scanned", b)).toList();
        return new CheckInSummary(expected, checkedIn, noShows, attendance, recent);
    }

    /** Fills in everything the door staff need to see for one booking. */
    private ScanResult result(String code, String message, Booking b) {
        BigDecimal paid = payments.findByBookingId(b.getId()).stream()
                .filter(p -> p.getStatus() != PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new ScanResult(code, message, b.getReference(), b.getShow().getMovie().getTitle(),
                b.getShow().getScreen().getTheatre().getName(), b.getShow().getScreen().getName(),
                b.getShow().getStartTime(), b.getSeatCodes().stream().sorted().toList(), paid,
                b.getCustomer().getFullName(), b.getCheckedInAt());
    }
}
