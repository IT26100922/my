package com.moviebooking.movie.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** Normal 2D screening. No surcharge. INHERITANCE from Movie. */
@Entity
@DiscriminatorValue("STANDARD")
public class StandardMovie extends Movie {

    public StandardMovie() {
    }

    @Override
    public String getFormat() {
        return "STANDARD";
    }

    @Override
    public BigDecimal getSurcharge() {
        return BigDecimal.ZERO;
    }
}
