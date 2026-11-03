package com.moviebooking.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs for the sales dashboard (READ-only reports). */
public final class ReportDto {

    private ReportDto() {
    }

    /** Headline numbers for the chosen period. Money is in LKR. */
    public record Totals(
            BigDecimal gross,
            BigDecimal refunds,
            BigDecimal net,
            BigDecimal fees,
            long payments,
            long tickets,
            long confirmedBookings,
            long cancelledBookings,
            long showCount,
            double occupancyPercent) {
    }

    /** One day in the revenue chart (zero-filled for days without sales). */
    public record DayPoint(String date, BigDecimal net, long tickets) {
    }

    /** One movie in the "top movies" chart. */
    public record MovieRow(String title, BigDecimal net, long tickets) {
    }

    /** One payment method in the donut chart. */
    public record MethodRow(String method, long count, BigDecimal amount) {
    }

    public record SalesReport(
            LocalDate from,
            LocalDate to,
            Totals totals,
            List<DayPoint> daily,
            List<MovieRow> byMovie,
            List<MethodRow> byMethod) {
    }
}
