package com.moviebooking.review.entity;

import com.moviebooking.movie.entity.Movie;
import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A star rating and optional comment that a customer leaves for a film after a confirmed,
 * already-started show. One review per booking, enforced by the unique booking_id column.
 * ENCAPSULATION: fields are set only through the constructor; reviews are not edited after posting.
 */
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_review_movie_created", columnList = "movie_id, created_at"),
        @Index(name = "idx_review_customer", columnList = "customer_id")
})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Review() {
        // required by JPA
    }

    public Review(Movie movie, Customer customer, Long bookingId, int rating, String comment, LocalDateTime createdAt) {
        this.movie = movie;
        this.customer = customer;
        this.bookingId = bookingId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Movie getMovie() { return movie; }
    public Customer getCustomer() { return customer; }
    public Long getBookingId() { return bookingId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
