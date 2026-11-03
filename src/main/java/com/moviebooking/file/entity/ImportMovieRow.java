package com.moviebooking.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One row of the MySQL import table {@code import_movies}.
 * <p>
 * This is the staging area for the "Import movies" button: the button never reads a file,
 * it copies rows from this table into the live {@code movies} table.
 * Fill this table with plain SQL (see database/cinemax_mysql_setup.sql) or from MySQL Workbench.
 */
@Entity
@Table(name = "import_movies")
public class ImportMovieRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 60)
    private String genre;

    @Column(length = 40)
    private String language;

    private Integer durationMinutes;

    @Column(length = 20)
    private String rating;

    /** STANDARD, IMAX or THREE_D - the same values the Movie entity accepts. */
    @Column(name = "movie_format", length = 20)
    private String movieFormat;

    private LocalDate releaseDate;

    @Column(length = 150)
    private String director;

    @Column(length = 300)
    private String producers;

    @Column(length = 300)
    private String writers;

    @Column(length = 500)
    private String castMembers;

    @Column(length = 500)
    private String trailerUrl;

    @Column(length = 2000)
    private String stillUrls;

    @Column(length = 1000)
    private String description;

    public ImportMovieRow() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getGenre() {
        return genre;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public String getRating() {
        return rating;
    }

    public String getMovieFormat() {
        return movieFormat;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public String getDirector() {
        return director;
    }

    public String getProducers() {
        return producers;
    }

    public String getWriters() {
        return writers;
    }

    public String getCastMembers() {
        return castMembers;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public String getStillUrls() {
        return stillUrls;
    }

    public String getDescription() {
        return description;
    }

    /** Used when the live table is copied back into the staging table. */
    public void setFrom(String title, String genre, String language, Integer durationMinutes, String rating,
                        String movieFormat, LocalDate releaseDate, String director, String producers,
                        String writers, String castMembers, String trailerUrl, String stillUrls,
                        String description) {
        this.title = title;
        this.genre = genre;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.rating = rating;
        this.movieFormat = movieFormat;
        this.releaseDate = releaseDate;
        this.director = director;
        this.producers = producers;
        this.writers = writers;
        this.castMembers = castMembers;
        this.trailerUrl = trailerUrl;
        this.stillUrls = stillUrls;
        this.description = description;
    }
}
