package com.moviebooking.movie.controller;

import com.moviebooking.file.service.FileService;
import com.moviebooking.movie.dto.MovieDto.FilterOptions;
import com.moviebooking.movie.dto.MovieDto.MovieRequest;
import com.moviebooking.movie.dto.MovieDto.MovieResponse;
import com.moviebooking.movie.service.MovieService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public movie browsing (READ) and admin CRUD under /api/admin/movies. */
@RestController
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    private final MovieService movieService;
    private final FileService fileService;

    public MovieController(MovieService movieService, FileService fileService) {
        this.movieService = movieService;
        this.fileService = fileService;
    }

    @GetMapping("/api/movies")
    public List<MovieResponse> search(@RequestParam(defaultValue = "") String title,
                                      @RequestParam(defaultValue = "") String genre,
                                      @RequestParam(defaultValue = "") String format,
                                      @RequestParam(defaultValue = "") String language,
                                      @RequestParam(defaultValue = "title") String sort) {
        return movieService.search(title, genre, format, language, sort);
    }

    @GetMapping("/api/movies/filters")
    public FilterOptions filters() {
        return movieService.filterOptions();
    }

    @GetMapping("/api/movies/{id}")
    public MovieResponse get(@PathVariable Long id) {
        return movieService.getById(id);
    }

    @PostMapping("/api/admin/movies")
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
        MovieResponse created = movieService.create(request);
        syncSeedFile();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/admin/movies/{id}")
    public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        MovieResponse updated = movieService.update(id, request);
        syncSeedFile();
        return updated;
    }

    @DeleteMapping("/api/admin/movies/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        movieService.delete(id);
        syncSeedFile();
        return Map.of("message", "Movie deleted");
    }

    /** Keeps the MySQL import table import_movies in step with the live table. A failure must not undo the saved change. */
    private void syncSeedFile() {
        try {
            fileService.exportMovies();
        } catch (RuntimeException ex) {
            log.warn("Movie saved, but the import_movies table could not be refreshed: {}", ex.getMessage());
        }
    }
}
