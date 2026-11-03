package com.moviebooking.theatre.entity;

import java.math.BigDecimal;

/**
 * Price band of a row inside a screen. The back rows are the best seats (the whole screen is in view),
 * so they cost more; the front rows are the cheapest. The multiplier is applied on top of the price the
 * pricing strategies compute, so the time-based rules keep working exactly as before.
 */
public enum SeatBand {

    REGULAR("Regular", BigDecimal.ONE),
    PREMIUM("Premium", new BigDecimal("1.20")),
    VIP("VIP lounge", new BigDecimal("1.45"));

    private final String label;
    private final BigDecimal multiplier;

    SeatBand(String label, BigDecimal multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }
}
