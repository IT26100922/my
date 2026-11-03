package com.moviebooking.show.controller;

import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.file.service.FileService;
import com.moviebooking.show.dto.ShowDto.ShowResponse;
import com.moviebooking.show.service.ShowService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Public showtime search (READ) and admin CRUD under /api/admin/shows. */
@RestController
public class ShowController {

    private static final Logger log = LoggerFactory.getLogger(ShowController.class);

    private final ShowService showService;
    private final FileService fileService;

    public ShowController(ShowService showService, FileService fileService) {
        this.showService = showService;
        this.fileService = fileService;
    }

    @GetMapping("/api/shows")
    public List<ShowResponse> search(@RequestParam(required = false) Long movieId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showService.search(movieId, date);
    }

    @GetMapping("/api/shows/{id}")
    public ShowResponse get(@PathVariable Long id) {
        return showService.getById(id);
    }

    @GetMapping("/api/admin/shows")
    public List<ShowResponse> listAll() {
        return showService.listAll();
    }

    @PostMapping("/api/admin/shows")
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowRequest request) {
        ShowResponse created = showService.create(request);
        syncSeedFile();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/admin/shows/{id}")
    public ShowResponse update(@PathVariable Long id, @Valid @RequestBody ShowRequest request) {
        ShowResponse updated = showService.update(id, request);
        syncSeedFile();
        return updated;
    }

    @DeleteMapping("/api/admin/shows/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        showService.delete(id);
        syncSeedFile();
        return Map.of("message", "Showtime deleted");
    }

    /** Keeps the MySQL import table import_showtimes in step with the live table. A failure must not undo the saved change. */
    private void syncSeedFile() {
        try {
            fileService.exportShowtimes();
        } catch (RuntimeException ex) {
            log.warn("Showtime saved, but the import_showtimes table could not be refreshed: {}", ex.getMessage());
        }
    }
}
