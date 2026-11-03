package com.moviebooking.booking.policy;

import java.math.BigDecimal;

/**
 * STRATEGY / ABSTRACTION for refunds on cancelled bookings.
 * Each policy decides whether it applies to a given notice period and how much to refund.
 */
public interface CancellationPolicy {

    /** True if this policy applies when the show starts in the given number of whole hours. */
    boolean supports(long hoursBeforeShow);

    /** Refund amount for a booking with this total. */
    BigDecimal refundFor(BigDecimal bookingTotal);

    /** Name shown to the customer. */
    String name();
}
