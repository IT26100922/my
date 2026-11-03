package com.moviebooking.movie.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** 3D screening. Adds a LKR 200 surcharge per ticket. INHERITANCE from Movie. */
@Entity
@DiscriminatorValue("THREE_D")
public class ThreeDMovie extends Movie {

    private static final BigDecimal SURCHARGE = new BigDecimal("200.00");

    public ThreeDMovie() {
    }

    @Override
    public String getFormat() {
        return "THREE_D";
    }

    @Override
    public BigDecimal getSurcharge() {
        return SURCHARGE;
    }
}
