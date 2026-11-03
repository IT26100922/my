package com.moviebooking.show.pricing;

import com.moviebooking.show.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pricing rules: which strategy applies to a showtime, which one wins when several match,
 * and the per-seat price each one produces.
 * 2026-10-17 is a Saturday, 2026-10-20 a Tuesday.
 */
@SpringBootTest
@ActiveProfiles("test")
class PricingRulesTest {

    private static final BigDecimal BASE = new BigDecimal("1000.00");

    @Autowired ShowService shows;

    @Test
    void weekdayEveningUsesStandardPrice() {
        LocalDateTime tuesdayEvening = LocalDateTime.of(2026, 10, 20, 18, 0);
        PricingStrategy s = shows.resolveStrategy(tuesdayEvening);
        assertThat(s).isInstanceOf(WeekdayPricingStrategy.class);
        assertThat(s.priceFor(BASE, tuesdayEvening)).isEqualByComparingTo("1000.00");
    }

    @Test
    void weekendEveningIsTwentyPercentDearer() {
        LocalDateTime saturdayEvening = LocalDateTime.of(2026, 10, 17, 18, 0);
        PricingStrategy s = shows.resolveStrategy(saturdayEvening);
        assertThat(s).isInstanceOf(WeekendPricingStrategy.class);
        assertThat(s.priceFor(BASE, saturdayEvening)).isEqualByComparingTo("1200.00");
    }

    @Test
    void morningShowIsTwentyPercentCheaperOnAnyDay() {
        LocalDateTime tuesdayMorning = LocalDateTime.of(2026, 10, 20, 11, 0);
        PricingStrategy s = shows.resolveStrategy(tuesdayMorning);
        assertThat(s).isInstanceOf(MatineePricingStrategy.class);
        assertThat(s.priceFor(BASE, tuesdayMorning)).isEqualByComparingTo("800.00");
    }

    @Test
    void matineeWinsOverWeekendWhenBothApply() {
        // Saturday before noon matches Weekend (priority 1) and Matinee (priority 2). Matinee wins.
        LocalDateTime saturdayMorning = LocalDateTime.of(2026, 10, 17, 10, 30);
        assertThat(shows.resolveStrategy(saturdayMorning)).isInstanceOf(MatineePricingStrategy.class);
    }

    @Test
    void noonIsNotMatinee() {
        // The matinee rule is "before 12:00", so a 12:00 show on a weekday uses the standard price.
        LocalDateTime tuesdayNoon = LocalDateTime.of(2026, 10, 20, 12, 0);
        assertThat(shows.resolveStrategy(tuesdayNoon)).isInstanceOf(WeekdayPricingStrategy.class);
        LocalDateTime tuesdayElevenFiftyNine = LocalDateTime.of(2026, 10, 20, 11, 59);
        assertThat(shows.resolveStrategy(tuesdayElevenFiftyNine)).isInstanceOf(MatineePricingStrategy.class);
    }

    @Test
    void priorityOrderIsMatineeThenWeekendThenWeekday() {
        assertThat(new MatineePricingStrategy().priority()).isGreaterThan(new WeekendPricingStrategy().priority());
        assertThat(new WeekendPricingStrategy().priority()).isGreaterThan(new WeekdayPricingStrategy().priority());
    }
}
