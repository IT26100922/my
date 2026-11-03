package com.moviebooking.theatre.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** VIP lounge auditorium: sofa seats, 80% higher price. */
@Entity
@DiscriminatorValue("VIP")
public class VipScreen extends Screen {

    public VipScreen() {
    }

    @Override
    public String getScreenType() {
        return "VIP";
    }

    @Override
    public String getCategory() {
        return "VIP Lounge";
    }

    @Override
    public BigDecimal getPriceMultiplier() {
        return new BigDecimal("1.80");
    }
}
