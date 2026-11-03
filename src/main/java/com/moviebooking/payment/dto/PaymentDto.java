package com.moviebooking.payment.dto;

import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentMethodType;
import com.moviebooking.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs for payments. */
public final class PaymentDto {

    private PaymentDto() {
    }

    public record PaymentRequest(
            @NotNull Long bookingId,
            @NotNull PaymentMethodType method,
            @NotBlank String reference) {
    }

    public record PaymentStatusRequest(@NotNull PaymentStatus status) {
    }

    public record PaymentResponse(
            Long id,
            String transactionRef,
            Long bookingId,
            String bookingReference,
            String movieTitle,
            String customerName,
            BigDecimal amount,
            BigDecimal convenienceFee,
            String method,
            String status,
            String bookingStatus,
            String maskedReference,
            BigDecimal refundedAmount,
            LocalDateTime paidAt,
            String description) {

        public static PaymentResponse from(Payment p, String description) {
            return new PaymentResponse(
                    p.getId(), p.getTransactionRef(), p.getBooking().getId(),
                    p.getBooking().getReference(),
                    p.getBooking().getShow().getMovie().getTitle(),
                    p.getBooking().getCustomer().getFullName(),
                    p.getAmount(), p.getConvenienceFee(), p.getMethod().name(), p.getStatus().name(),
                    p.getBooking().getStatus().name(),
                    p.getMaskedReference(), p.getRefundedAmount(), p.getPaidAt(), description);
        }
    }
}
