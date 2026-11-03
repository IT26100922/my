package com.moviebooking.movie.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** IMAX screening. Adds a LKR 300 surcharge per ticket. INHERITANCE from Movie. */
@Entity
@DiscriminatorValue("IMAX")
public class ImaxMovie extends Movie {

    private static final BigDecimal SURCHARGE = new BigDecimal("300.00");

    public ImaxMovie() {
    }

    @Override
    public String getFormat() {
        return "IMAX";
    }

    @Override
    public BigDecimal getSurcharge() {
        return SURCHARGE;
    }
}
