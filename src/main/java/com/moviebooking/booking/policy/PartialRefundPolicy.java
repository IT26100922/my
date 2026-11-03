package com.moviebooking.booking.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cancelled between 2 and 24 hours before the show: 50% refund. */
@Component
public class PartialRefundPolicy implements CancellationPolicy {

    private static final BigDecimal HALF = new BigDecimal("0.50");

    @Override
    public boolean supports(long hoursBeforeShow) {
        return hoursBeforeShow >= 2 && hoursBeforeShow < 24;
    }

    @Override
    public BigDecimal refundFor(BigDecimal bookingTotal) {
        return bookingTotal.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "50% refund (cancelled 2-24h before show)";
    }
}
