package com.moviebooking.review.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.review.dto.ReviewDto.Eligibility;
import com.moviebooking.review.dto.ReviewDto.MovieReviews;
import com.moviebooking.review.dto.ReviewDto.ReviewRequest;
import com.moviebooking.review.dto.ReviewDto.ReviewResponse;
import com.moviebooking.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public reading of film reviews (/api/movies/{id}/reviews) and customer posting
 * (/api/customer/reviews). The customer endpoints are guarded by AuthInterceptor.
 */
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/movies/{id}/reviews")
    public MovieReviews forMovie(@PathVariable Long id) {
        return reviewService.forMovie(id);
    }

    /** 200 with the booking to review, or 204 when the customer has nothing to review for this film. */
    @GetMapping("/api/customer/reviews/eligible")
    public ResponseEntity<Eligibility> eligible(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                                @RequestParam Long movieId) {
        Eligibility e = reviewService.eligibleBooking(userId, movieId);
        return e == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(e);
    }

    @PostMapping("/api/customer/reviews")
    public ResponseEntity<ReviewResponse> create(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(userId, request));
    }
}
