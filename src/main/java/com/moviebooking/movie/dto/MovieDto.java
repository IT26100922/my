package com.moviebooking.movie.dto;

import com.moviebooking.movie.entity.Movie;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** DTOs for the movie catalogue. */
public final class MovieDto {

    private MovieDto() {
    }

    public record MovieRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 60) String genre,
            @NotBlank @Size(max = 40) String language,
            @NotNull @Min(30) @Max(400) Integer durationMinutes,
            @Size(max = 10) String rating,
            @Size(max = 1000) String description,
            @Size(max = 500) String posterUrl,
            LocalDate releaseDate,
            @NotBlank String format,
            @Size(max = 150) String director,
            @Size(max = 300) String producers,
            @Size(max = 300) String writers,
            @Size(max = 500) String castMembers,
            @Size(max = 500) String trailerUrl,
            @Size(max = 2000) String stillUrls) {
    }

    /** Distinct values the search page offers in its dropdowns. */
    public record FilterOptions(List<String> genres, List<String> formats, List<String> languages) {
    }

    /** Read-only view of a movie (exposes the polymorphic format and surcharge). */
    public record MovieResponse(
            Long id,
            String title,
            String genre,
            String language,
            Integer durationMinutes,
            String rating,
            String description,
            String posterUrl,
            LocalDate releaseDate,
            String format,
            BigDecimal surcharge,
            String director,
            String producers,
            String writers,
            String castMembers,
            String trailerUrl,
            List<String> stills) {

        public static MovieResponse from(Movie m) {
            return new MovieResponse(m.getId(), m.getTitle(), m.getGenre(), m.getLanguage(),
                    m.getDurationMinutes(), m.getRating(), m.getDescription(), m.getPosterUrl(),
                    m.getReleaseDate(), m.getFormat(), m.getSurcharge(),
                    m.getDirector(), m.getProducers(), m.getWriters(), m.getCastMembers(),
                    m.getTrailerUrl(), splitStills(m.getStillUrls()));
        }

        /** Splits "url1, url2" into a list, ignoring blank entries. */
        static List<String> splitStills(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
