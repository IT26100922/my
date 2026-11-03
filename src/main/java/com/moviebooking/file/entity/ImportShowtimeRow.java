package com.moviebooking.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the MySQL import table {@code import_showtimes}.
 * <p>
 * The staging area for the "Import showtimes" button. The movie, theatre and screen are named
 * (not by id) so the row stays readable, and they are looked up in the live tables during the import.
 */
@Entity
@Table(name = "import_showtimes")
public class ImportShowtimeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String movieTitle;

    @Column(nullable = false, length = 120)
    private String theatreName;

    @Column(nullable = false, length = 120)
    private String screenName;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    public ImportShowtimeRow() {
    }

    public Long getId() {
        return id;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public String getTheatreName() {
        return theatreName;
    }

    public String getScreenName() {
        return screenName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    /** Used when the live table is copied back into the staging table. */
    public void setFrom(String movieTitle, String theatreName, String screenName,
                        LocalDateTime startTime, BigDecimal basePrice) {
        this.movieTitle = movieTitle;
        this.theatreName = theatreName;
        this.screenName = screenName;
        this.startTime = startTime;
        this.basePrice = basePrice;
    }
}
