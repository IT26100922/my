package com.moviebooking.promo.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A discount code. STRATEGY: the discount rule lives in this entity, so a booking only has to ask
 * "what do I get off this total?" and never repeats the arithmetic.
 */
@Entity
@Table(name = "promo_codes", indexes = {
        @Index(name = "idx_promo_code", columnList = "code", unique = true)
})
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PromoType type;

    /** PERCENT: the percentage. FLAT: the amount in LKR. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** The booking must be worth at least this much before the code applies. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minSpend;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validUntil;

    /** How many times the code may be used in total. Zero or null means unlimited. */
    private Integer maxUses;

    @Column(nullable = false)
    private int usedCount;

    @Column(length = 160)
    private String description;

    protected PromoCode() {
        // required by JPA
    }

    public PromoCode(String code, PromoType type, BigDecimal value, BigDecimal minSpend,
                     LocalDate validFrom, LocalDate validUntil, Integer maxUses, String description) {
        this.code = normalize(code);
        this.type = type;
        this.value = value;
        this.minSpend = minSpend == null ? BigDecimal.ZERO : minSpend;
        this.validFrom = validFrom == null ? LocalDate.now() : validFrom;
        this.validUntil = validUntil == null ? LocalDate.now().plusYears(1) : validUntil;
        this.maxUses = maxUses;
        this.usedCount = 0;
        this.description = description;
    }

    /** Codes are written in capitals and compared in capitals, so "welcome10" works like "WELCOME10". */
    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase().replace(" ", "");
    }

    /** True when the code may be used today, has uses left and the booking is big enough. */
    public boolean isUsableFor(BigDecimal total, LocalDate today) {
        if (total == null || total.compareTo(minSpend) < 0) {
            return false;
        }
        if (today.isBefore(validFrom) || today.isAfter(validUntil)) {
            return false;
        }
        return maxUses == null || maxUses <= 0 || usedCount < maxUses;
    }

    /** The money this code takes off a booking worth the given total. Never more than the total. */
    public BigDecimal discountFor(BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal discount = type == PromoType.PERCENT
                ? total.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;
        return discount.min(total).setScale(2, RoundingMode.HALF_UP);
    }

    /** Why the code cannot be used, in words a customer can act on. */
    public String refusalReason(BigDecimal total, LocalDate today) {
        if (total != null && total.compareTo(minSpend) < 0) {
            return "This code needs a booking of at least LKR " + minSpend.setScale(2) + "";
        }
        if (today.isBefore(validFrom)) {
            return "This code is not active yet";
        }
        if (today.isAfter(validUntil)) {
            return "This code expired on " + validUntil;
        }
        if (maxUses != null && maxUses > 0 && usedCount >= maxUses) {
            return "This code has been used up";
        }
        return "This code cannot be used for this booking";
    }

    public void recordUse() {
        this.usedCount++;
    }

    public void update(String code, PromoType type, BigDecimal value, BigDecimal minSpend, LocalDate validFrom,
                       LocalDate validUntil, Integer maxUses, String description) {
        this.code = normalize(code);
        this.type = type;
        this.value = value;
        this.minSpend = minSpend == null ? BigDecimal.ZERO : minSpend;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.maxUses = maxUses;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public PromoType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinSpend() {
        return minSpend;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public String getDescription() {
        return description;
    }
}
