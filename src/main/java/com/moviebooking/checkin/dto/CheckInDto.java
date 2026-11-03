package com.moviebooking.checkin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for scanning tickets at the door. */
public final class CheckInDto {

    private CheckInDto() {
    }

    /**
     * Outcome of a scan. result is one of:
     * OK, ALREADY_USED, CANCELLED, NOT_PAID, NOT_FOUND, WRONG_DAY, NOT_STARTED_TODAY.
     */
    public record ScanResult(
            String result,
            String message,
            String reference,
            String movieTitle,
            String theatreName,
            String screenName,
            LocalDateTime showTime,
            List<String> seatCodes,
            BigDecimal paidAmount,
            String customerName,
            LocalDateTime checkedInAt) {
    }

    /** What the door staff see at the top of the page. */
    public record CheckInSummary(
            long expected,
            long checkedIn,
            long noShows,
            double attendancePercent,
            List<ScanResult> recentScans) {
    }
}
