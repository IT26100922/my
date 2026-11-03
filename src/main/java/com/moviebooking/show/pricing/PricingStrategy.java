package com.moviebooking.show.pricing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * STRATEGY pattern for ticket pricing.
 * ABSTRACTION: ShowService asks for "the price" and does not know which rule was used.
 * POLYMORPHISM: Weekday, Weekend and Matinee strategies each implement their own rule.
 */
public interface PricingStrategy {

    /** True if this rule applies to a show starting at the given time. */
    boolean supports(LocalDateTime showTime);

    /** Price per seat for a show at this time. */
    BigDecimal priceFor(BigDecimal basePrice, LocalDateTime showTime);

    /** Name shown to the user, e.g. "Weekend". */
    String name();

    /** When several rules match, the highest priority wins. */
    int priority();
}
