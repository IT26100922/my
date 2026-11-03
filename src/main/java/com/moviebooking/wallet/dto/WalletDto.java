package com.moviebooking.wallet.dto;

import com.moviebooking.wallet.entity.WalletTransaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for the customer wallet. */
public final class WalletDto {

    private WalletDto() {
    }

    public record TopUpRequest(
            @NotNull @DecimalMin("50.00") BigDecimal amount,
            String method) {
    }

    public record WalletEntry(
            Long id,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String note,
            LocalDateTime createdAt) {

        public static WalletEntry from(WalletTransaction t) {
            return new WalletEntry(t.getId(), t.getType().name(), t.getAmount(), t.getBalanceAfter(),
                    t.getNote(), t.getCreatedAt());
        }
    }

    /** The balance plus the last 50 movements, newest first. */
    public record WalletResponse(
            BigDecimal balance,
            List<WalletEntry> entries) {
    }
}
