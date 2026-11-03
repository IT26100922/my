package com.moviebooking.booking.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Cancelled less than 2 hours before the show (or after it started): no refund. */
@Component
public class NoRefundPolicy implements CancellationPolicy {

    @Override
    public boolean supports(long hoursBeforeShow) {
        return hoursBeforeShow < 2;
    }

    @Override
    public BigDecimal refundFor(BigDecimal bookingTotal) {
        return BigDecimal.ZERO.setScale(2);
    }

    @Override
    public String name() {
        return "No refund (cancelled less than 2h before show)";
    }
}
