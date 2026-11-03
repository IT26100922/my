package com.moviebooking.booking.dto;

import com.moviebooking.booking.entity.Booking;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for bookings. */
public final class BookingDto {

    private BookingDto() {
    }

    public record BookingRequest(
            @NotNull Long showId,
            @NotEmpty List<String> seatCodes,
            String promoCode) {
    }

    /** A promo code that was accepted for a basket: what it took off and what is left to pay. */
    public record PromoQuote(
            String code,
            BigDecimal discount,
            BigDecimal newTotal) {
    }

    /** Giving part of a booking back: the seats the customer no longer wants. */
    public record CancelSeatsRequest(
            @NotEmpty List<String> seatCodes) {
    }

    public record BookingUpdateRequest(
            @NotEmpty List<String> seatCodes) {
    }

    public record BookingResponse(
            Long id,
            String reference,
            Long showId,
            Long movieId,
            String movieTitle,
            String theatreName,
            String screenName,
            LocalDateTime showTime,
            List<String> seatCodes,
            BigDecimal totalAmount,
            String status,
            String customerName,
            String customerEmail,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            long secondsLeft,
            BigDecimal discountAmount,
            String promoCode,
            LocalDateTime checkedInAt,
            String checkedInBy) {

        public static BookingResponse from(Booking b) {
            return new BookingResponse(
                    b.getId(), b.getReference(), b.getShow().getId(), b.getShow().getMovie().getId(),
                    b.getShow().getMovie().getTitle(),
                    b.getShow().getScreen().getTheatre().getName(),
                    b.getShow().getScreen().getName(),
                    b.getShow().getStartTime(),
                    b.getSeatCodes().stream().sorted().toList(),
                    b.getTotalAmount(), b.getStatus().name(),
                    b.getCustomer().getFullName(), b.getCustomer().getEmail(),
                    b.getCreatedAt(), b.getExpiresAt(), secondsLeft(b),
                    b.getDiscountAmount(), b.getPromoCode(), b.getCheckedInAt(), b.getCheckedInBy());
        }

        /** Seconds until the seat hold ends (0 when expired, -1 when the booking has no hold). */
        private static long secondsLeft(Booking b) {
            if (b.getExpiresAt() == null) {
                return -1;
            }
            return Math.max(0, java.time.Duration.between(LocalDateTime.now(), b.getExpiresAt()).getSeconds());
        }
    }

    /**
     * Moves a confirmed booking to another showtime of the same film. The customer picks the same number of seats
     * on the new show. If the new show costs more, the difference is charged with the given method. If it costs less,
     * the difference is refunded to the original payments.
     */
    public record RescheduleRequest(
            @NotNull Long showId,
            @NotEmpty List<String> seatCodes,
            String method,
            String reference) {
    }

    /** What a cancellation would refund right now, before the customer confirms it. */
    public record CancelPreview(
            String policy,
            BigDecimal refundAmount,
            long hoursBeforeShow,
            boolean awaitingPayment) {
    }

    /** Result of a cancellation: what was refunded and under which policy. */
    public record CancellationResult(
            String message,
            String policy,
            BigDecimal refundAmount,
            String bookingStatus) {
    }
}
