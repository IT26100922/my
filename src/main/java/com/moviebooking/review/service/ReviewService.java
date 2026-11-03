package com.moviebooking.review.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.review.dto.ReviewDto.Eligibility;
import com.moviebooking.review.dto.ReviewDto.MovieReviews;
import com.moviebooking.review.dto.ReviewDto.ReviewRequest;
import com.moviebooking.review.dto.ReviewDto.ReviewResponse;
import com.moviebooking.review.entity.Review;
import com.moviebooking.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ratings and reviews. A customer may review a film only for their own confirmed booking, and only
 * after that show has started. Each booking can be reviewed once.
 */
@Service
public class ReviewService {

    private static final int MAX_COMMENT = 500;

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final MovieRepository movies;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings, MovieRepository movies) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.movies = movies;
    }

    @Transactional(readOnly = true)
    public MovieReviews forMovie(Long movieId) {
        if (!movies.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie not found");
        }
        List<ReviewResponse> items = reviews.findByMovieIdOrderByCreatedAtDesc(movieId).stream()
                .map(this::toResponse)
                .toList();
        return new MovieReviews(reviews.averageRating(movieId), reviews.countByMovieId(movieId), items);
    }

    /** The customer's most recent confirmed booking for this film whose show has started and is not yet reviewed. */
    @Transactional(readOnly = true)
    public Eligibility eligibleBooking(Long customerId, Long movieId) {
        LocalDateTime now = LocalDateTime.now();
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> b.getShow().getMovie().getId().equals(movieId))
                .filter(b -> !b.getShow().getStartTime().isAfter(now))
                .filter(b -> !reviews.existsByBookingId(b.getId()))
                .findFirst()
                .map(b -> new Eligibility(b.getId(), b.getReference(), b.getShow().getMovie().getTitle()))
                .orElse(null);
    }

    @Transactional
    public ReviewResponse create(Long customerId, ReviewRequest request) {
        Booking booking = bookings.findById(request.bookingId())
                .filter(b -> b.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only confirmed bookings can be reviewed");
        }
        if (booking.getShow().getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("You can review a film once the show has started");
        }
        if (reviews.existsByBookingId(booking.getId())) {
            throw new BusinessException("You have already reviewed this booking");
        }
        Movie movie = booking.getShow().getMovie();
        Review review = new Review(movie, booking.getCustomer(), booking.getId(), request.rating(),
                cleanComment(request.comment()), LocalDateTime.now());
        return toResponse(reviews.save(review));
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getCustomer().getFullName(), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    private String cleanComment(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_COMMENT ? trimmed.substring(0, MAX_COMMENT) : trimmed;
    }
}
