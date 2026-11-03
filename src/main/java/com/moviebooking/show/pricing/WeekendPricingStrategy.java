package com.moviebooking.show.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/** Saturday and Sunday shows cost 20% more. */
@Component
public class WeekendPricingStrategy implements PricingStrategy {

    private static final BigDecimal MULTIPLIER = new BigDecimal("1.20");

    @Override
    public boolean supports(LocalDateTime showTime) {
        DayOfWeek day = showTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    @Override
    public BigDecimal priceFor(BigDecimal basePrice, LocalDateTime showTime) {
        return basePrice.multiply(MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "Weekend";
    }

    @Override
    public int priority() {
        return 1;
    }
}
