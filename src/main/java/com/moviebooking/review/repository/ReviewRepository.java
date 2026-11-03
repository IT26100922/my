package com.moviebooking.review.repository;

import com.moviebooking.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMovieIdOrderByCreatedAtDesc(Long movieId);

    boolean existsByBookingId(Long bookingId);

    long countByMovieId(Long movieId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.movie.id = :movieId")
    double averageRating(@Param("movieId") Long movieId);
}
