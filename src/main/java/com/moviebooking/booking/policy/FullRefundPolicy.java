package com.moviebooking.booking.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Cancelled 24 hours or more before the show: 100% refund. */
@Component
public class FullRefundPolicy implements CancellationPolicy {

    @Override
    public boolean supports(long hoursBeforeShow) {
        return hoursBeforeShow >= 24;
    }

    @Override
    public BigDecimal refundFor(BigDecimal bookingTotal) {
        return bookingTotal;
    }

    @Override
    public String name() {
        return "Full refund (cancelled 24h+ before show)";
    }
}
