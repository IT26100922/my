package com.moviebooking.movie.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Abstract movie. Each concrete screening format is a subclass:
 * StandardMovie, ImaxMovie, ThreeDMovie.
 * <p>
 * INHERITANCE + POLYMORPHISM: getFormat() and getSurcharge() behave differently per subclass.
 * ENCAPSULATION: private fields, accessed through getters and setters.
 */
@Entity
@Table(name = "movies")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "movie_format", discriminatorType = DiscriminatorType.STRING)
public abstract class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 60)
    private String genre;

    @Column(nullable = false, length = 40)
    private String language;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(length = 10)
    private String rating;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String posterUrl;

    private LocalDate releaseDate;

    /* Credits and media shown on the movie details page. */
    @Column(length = 150)
    private String director;

    @Column(length = 300)
    private String producers;

    @Column(length = 300)
    private String writers;

    @Column(name = "cast_members", length = 500)
    private String castMembers;

    @Column(length = 500)
    private String trailerUrl;

    /** Comma-separated image URLs (scene stills). */
    @Column(length = 2000)
    private String stillUrls;

    protected Movie() {
        // required by JPA
    }

    /** POLYMORPHISM: the screening format code, decided by the subclass. */
    public abstract String getFormat();

    /** POLYMORPHISM: extra ticket charge for this format (LKR), decided by the subclass. */
    public abstract BigDecimal getSurcharge();

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes < 30) {
            throw new IllegalArgumentException("Duration must be at least 30 minutes");
        }
        this.durationMinutes = durationMinutes;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getProducers() {
        return producers;
    }

    public void setProducers(String producers) {
        this.producers = producers;
    }

    public String getWriters() {
        return writers;
    }

    public void setWriters(String writers) {
        this.writers = writers;
    }

    public String getCastMembers() {
        return castMembers;
    }

    public void setCastMembers(String castMembers) {
        this.castMembers = castMembers;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    public String getStillUrls() {
        return stillUrls;
    }

    public void setStillUrls(String stillUrls) {
        this.stillUrls = stillUrls;
    }
}
