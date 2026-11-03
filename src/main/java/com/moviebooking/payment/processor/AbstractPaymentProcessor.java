package com.moviebooking.payment.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * TEMPLATE METHOD: charge() defines the fixed steps (validate -> fee -> reference -> receipt).
 * Subclasses only fill in the parts that differ (validation, fee, masking).
 * ABSTRACTION: callers never see those steps, only charge().
 */
public abstract class AbstractPaymentProcessor implements PaymentProcessor {

    @Override
    public final PaymentCharge charge(BigDecimal bookingTotal, String reference) {
        validateReference(reference);                                   // step 1: subclass rule
        BigDecimal fee = convenienceFee(bookingTotal).setScale(2, RoundingMode.HALF_UP); // step 2
        String txn = "TXN-" + method().name().replace("_", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();          // step 3
        return new PaymentCharge(bookingTotal.add(fee), fee, maskReference(reference), txn);
    }
}
