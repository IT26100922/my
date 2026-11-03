package com.moviebooking.theatre.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** Standard auditorium: base price, normal seats. */
@Entity
@DiscriminatorValue("STANDARD")
public class StandardScreen extends Screen {

    public StandardScreen() {
    }

    @Override
    public String getScreenType() {
        return "STANDARD";
    }

    @Override
    public String getCategory() {
        return "Standard 2D";
    }

    @Override
    public BigDecimal getPriceMultiplier() {
        return new BigDecimal("1.00");
    }
}
