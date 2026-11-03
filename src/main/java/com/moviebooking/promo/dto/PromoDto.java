package com.moviebooking.promo.dto;

import com.moviebooking.promo.entity.PromoCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs for promo codes. */
public final class PromoDto {

    private PromoDto() {
    }

    public record PromoRequest(
            @NotBlank String code,
            @NotNull String type,
            @NotNull BigDecimal value,
            BigDecimal minSpend,
            LocalDate validFrom,
            LocalDate validUntil,
            Integer maxUses,
            String description) {
    }

    public record PromoResponse(
            Long id,
            String code,
            String type,
            BigDecimal value,
            BigDecimal minSpend,
            LocalDate validFrom,
            LocalDate validUntil,
            Integer maxUses,
            int usedCount,
            boolean active,
            String description) {

        public static PromoResponse from(PromoCode p) {
            LocalDate today = LocalDate.now();
            boolean active = !today.isBefore(p.getValidFrom()) && !today.isAfter(p.getValidUntil())
                    && (p.getMaxUses() == null || p.getMaxUses() <= 0 || p.getUsedCount() < p.getMaxUses());
            return new PromoResponse(p.getId(), p.getCode(), p.getType().name(), p.getValue(), p.getMinSpend(),
                    p.getValidFrom(), p.getValidUntil(), p.getMaxUses(), p.getUsedCount(), active, p.getDescription());
        }
    }

    /** What the customer asks: a code and the value of the seats in the basket. */
    public record PromoCheckRequest(
            @NotBlank String code,
            @NotNull BigDecimal total) {
    }

    /** The answer: whether the code applies, what it takes off, and the new total to pay. */
    public record PromoCheckResponse(
            String code,
            boolean valid,
            BigDecimal discount,
            BigDecimal newTotal,
            String message) {
    }
}
