package com.moviebooking.show.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Shows that start before 12:00 (matinee) get a 20% discount. Highest priority. */
@Component
public class MatineePricingStrategy implements PricingStrategy {

    private static final BigDecimal MULTIPLIER = new BigDecimal("0.80");

    @Override
    public boolean supports(LocalDateTime showTime) {
        return showTime.getHour() < 12;
    }

    @Override
    public BigDecimal priceFor(BigDecimal basePrice, LocalDateTime showTime) {
        return basePrice.multiply(MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "Matinee";
    }

    @Override
    public int priority() {
        return 2;
    }
}
