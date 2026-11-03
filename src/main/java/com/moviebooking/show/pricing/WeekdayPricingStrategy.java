package com.moviebooking.show.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Default rule: the base price applies (used when no other rule matches). */
@Component
public class WeekdayPricingStrategy implements PricingStrategy {

    @Override
    public boolean supports(LocalDateTime showTime) {
        return true;
    }

    @Override
    public BigDecimal priceFor(BigDecimal basePrice, LocalDateTime showTime) {
        return basePrice;
    }

    @Override
    public String name() {
        return "Standard";
    }

    @Override
    public int priority() {
        return 0;
    }
}
