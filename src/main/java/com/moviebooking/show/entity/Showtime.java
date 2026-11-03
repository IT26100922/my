package com.moviebooking.show.entity;

import com.moviebooking.movie.entity.Movie;
import com.moviebooking.theatre.entity.Screen;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A scheduled screening: one movie on one screen at one time.
 * ENCAPSULATION: end time is derived from the movie duration, never set directly.
 */
@Entity
@Table(name = "showtimes", indexes = {
        @Index(name = "idx_showtime_movie_start", columnList = "movie_id, start_time"),
        @Index(name = "idx_showtime_screen_start", columnList = "screen_id, start_time")
})
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    /** Base price per seat before screen multiplier, movie surcharge and time-based pricing. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    protected Showtime() {
        // required by JPA
    }

    public Showtime(Movie movie, Screen screen, LocalDateTime startTime, BigDecimal basePrice) {
        updateDetails(movie, screen, startTime, basePrice);
    }

    /** Changes the schedule. The end time is recalculated from the movie length. */
    public void updateDetails(Movie movie, Screen screen, LocalDateTime startTime, BigDecimal basePrice) {
        if (basePrice == null || basePrice.signum() <= 0) {
            throw new IllegalArgumentException("Base price must be greater than zero");
        }
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.endTime = startTime.plusMinutes(movie.getDurationMinutes());
        this.basePrice = basePrice;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }
}
