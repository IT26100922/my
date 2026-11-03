package com.moviebooking.payment.processor;

import java.math.BigDecimal;

/** Result of a successful charge, produced by a processor. */
public record PaymentCharge(
        BigDecimal totalAmount,
        BigDecimal convenienceFee,
        String maskedReference,
        String transactionRef) {
}
