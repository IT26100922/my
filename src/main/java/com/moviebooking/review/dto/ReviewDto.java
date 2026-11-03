package com.moviebooking.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewDto {

    private ReviewDto() {
    }

    /** Body of POST /api/customer/reviews. The booking must belong to the signed-in customer. */
    public record ReviewRequest(
            @NotNull Long bookingId,
            @Min(1) @Max(5) int rating,
            @Size(max = 500) String comment) {
    }

    public record ReviewResponse(
            Long id,
            String customerName,
            int rating,
            String comment,
            LocalDateTime createdAt) {
    }

    /** Public summary for a film. average is 0 when there are no reviews yet. */
    public record MovieReviews(
            double average,
            long count,
            List<ReviewResponse> items) {
    }

    /** A booking the signed-in customer can review now. */
    public record Eligibility(
            Long bookingId,
            String reference,
            String movieTitle) {
    }
}
