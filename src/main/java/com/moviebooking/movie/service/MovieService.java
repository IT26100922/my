package com.moviebooking.movie.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.movie.dto.MovieDto.FilterOptions;
import com.moviebooking.movie.dto.MovieDto.MovieRequest;
import com.moviebooking.movie.dto.MovieDto.MovieResponse;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.show.repository.ShowtimeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * CRUD for movies.
 * CREATE: create()   READ: search() / getById()   UPDATE: update()   DELETE: delete()
 */
@Service
@Transactional
public class MovieService {

    private final MovieRepository movies;
    private final MovieFactory movieFactory;
    private final ShowtimeRepository showtimes;

    public MovieService(MovieRepository movies, MovieFactory movieFactory, ShowtimeRepository showtimes) {
        this.movies = movies;
        this.movieFactory = movieFactory;
        this.showtimes = showtimes;
    }

    /** READ: search by title and/or genre. */
    @Transactional(readOnly = true)
    public List<MovieResponse> search(String title, String genre) {
        return movies.search(clean(title), clean(genre)).stream().map(MovieResponse::from).toList();
    }

    /**
     * READ: search with filters. Title and genre are "contains" matches, format and language are exact
     * (language is case-insensitive), and sort is one of title (default), duration or newest.
     */
    @Transactional(readOnly = true)
    public List<MovieResponse> search(String title, String genre, String format, String language, String sort) {
        String wantedFormat = clean(format);
        String wantedLanguage = clean(language).toLowerCase(Locale.ROOT);
        return movies.search(clean(title), clean(genre)).stream()
                .filter(m -> wantedFormat.isEmpty() || m.getFormat().equalsIgnoreCase(wantedFormat))
                .filter(m -> wantedLanguage.isEmpty() || m.getLanguage().toLowerCase(Locale.ROOT).equals(wantedLanguage))
                .sorted(orderFor(clean(sort)))
                .map(MovieResponse::from)
                .toList();
    }

    /** READ: the values the search page can filter by (taken from the catalogue, so they always match). */
    @Transactional(readOnly = true)
    public FilterOptions filterOptions() {
        List<Movie> all = movies.findAll();
        TreeSet<String> genres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> formats = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> languages = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Movie m : all) {
            genres.add(m.getGenre().trim());
            formats.add(m.getFormat());
            languages.add(m.getLanguage().trim());
        }
        return new FilterOptions(List.copyOf(genres), List.copyOf(formats), List.copyOf(languages));
    }

    private static Comparator<Movie> orderFor(String sort) {
        return switch (sort) {
            case "duration" -> Comparator.comparing(Movie::getDurationMinutes, Comparator.nullsLast(Comparator.naturalOrder()));
            case "newest" -> Comparator.comparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER);
        };
    }

    /** READ: one movie. */
    @Transactional(readOnly = true)
    public MovieResponse getById(Long id) {
        return MovieResponse.from(find(id));
    }

    /** CREATE: the factory builds the right subclass for the requested format. */
    public MovieResponse create(MovieRequest req) {
        if (movies.existsByTitleIgnoreCase(req.title().trim())) {
            throw new BusinessException("A movie with this title already exists");
        }
        Movie movie = movieFactory.create(req.format());
        apply(movie, req);
        return MovieResponse.from(movies.save(movie));
    }

    /** UPDATE: the format is fixed once created (JPA cannot change a row's subclass). */
    public MovieResponse update(Long id, MovieRequest req) {
        Movie movie = find(id);
        if (!movie.getFormat().equalsIgnoreCase(req.format().trim())) {
            throw new BusinessException("Movie format cannot be changed. Create a new movie instead.");
        }
        apply(movie, req);
        return MovieResponse.from(movie);
    }

    /** DELETE: blocked while showtimes still reference the movie. */
    public void delete(Long id) {
        Movie movie = find(id);
        if (showtimes.existsByMovieId(id)) {
            throw new BusinessException("This movie has scheduled showtimes. Delete those first.");
        }
        movies.delete(movie);
    }

    /** READ every movie in id order (used to keep the import_movies table in step with the live table). */
    @Transactional(readOnly = true)
    public List<Movie> allForExport() {
        return movies.findAll(Sort.by("id"));
    }

    /** Used by the file importer to skip duplicates. */
    @Transactional(readOnly = true)
    public boolean titleExists(String title) {
        return movies.existsByTitleIgnoreCase(title.trim());
    }

    private Movie find(Long id) {
        return movies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + id));
    }

    private void apply(Movie movie, MovieRequest req) {
        movie.setTitle(req.title().trim());
        movie.setGenre(req.genre().trim());
        movie.setLanguage(req.language().trim());
        movie.setDurationMinutes(req.durationMinutes());
        movie.setRating(blankToNull(req.rating()));
        movie.setDescription(blankToNull(req.description()));
        movie.setPosterUrl(blankToNull(req.posterUrl()));
        movie.setReleaseDate(req.releaseDate());
        movie.setDirector(blankToNull(req.director()));
        movie.setProducers(blankToNull(req.producers()));
        movie.setWriters(blankToNull(req.writers()));
        movie.setCastMembers(blankToNull(req.castMembers()));
        movie.setTrailerUrl(blankToNull(req.trailerUrl()));
        movie.setStillUrls(blankToNull(req.stillUrls()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
