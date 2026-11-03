package com.moviebooking.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTOs for the admin analytics page: money, people and the funnel, in one payload. */
public final class AnalyticsDto {

    private AnalyticsDto() {
    }

    /** A bar or a point: a label and its value, plus a count where it makes sense. */
    public record Slice(
            String label,
            BigDecimal value,
            long count) {
    }

    /** Views → seat holds → paid → scanned. The top of the funnel is every booking row that was created. */
    public record Funnel(
            long seatHolds,
            long paidBookings,
            long cancelled,
            long checkedIn,
            double conversionPercent) {
    }

    public record AnalyticsResponse(
            int days,
            BigDecimal revenue,
            long ticketsSold,
            long bookings,
            BigDecimal averageBookingValue,
            double occupancyPercent,
            long noShows,
            Funnel funnel,
            List<Slice> revenueByMovie,
            List<Slice> revenueByScreen,
            List<Slice> revenueByDay,
            List<Slice> occupancyByHour,
            List<Slice> paymentMethods) {
    }
}
