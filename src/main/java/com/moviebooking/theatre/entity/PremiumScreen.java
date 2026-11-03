package com.moviebooking.theatre.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** Premium auditorium: recliner seats, 30% higher price. */
@Entity
@DiscriminatorValue("PREMIUM")
public class PremiumScreen extends Screen {

    public PremiumScreen() {
    }

    @Override
    public String getScreenType() {
        return "PREMIUM";
    }

    @Override
    public String getCategory() {
        return "Premium Recliner";
    }

    @Override
    public BigDecimal getPriceMultiplier() {
        return new BigDecimal("1.30");
    }
}
