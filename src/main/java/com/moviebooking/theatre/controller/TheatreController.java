package com.moviebooking.theatre.controller;

import com.moviebooking.theatre.dto.TheatreDto.TheatreRequest;
import com.moviebooking.theatre.dto.TheatreDto.TheatreResponse;
import com.moviebooking.theatre.service.TheatreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public theatre listing (READ) and admin CRUD under /api/admin/theatres. */
@RestController
public class TheatreController {

    private final TheatreService theatreService;

    public TheatreController(TheatreService theatreService) {
        this.theatreService = theatreService;
    }

    @GetMapping("/api/theatres")
    public List<TheatreResponse> list() {
        return theatreService.list();
    }

    @GetMapping("/api/theatres/{id}")
    public TheatreResponse get(@PathVariable Long id) {
        return theatreService.get(id);
    }

    @PostMapping("/api/admin/theatres")
    public ResponseEntity<TheatreResponse> create(@Valid @RequestBody TheatreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.create(request));
    }

    @PutMapping("/api/admin/theatres/{id}")
    public TheatreResponse update(@PathVariable Long id, @Valid @RequestBody TheatreRequest request) {
        return theatreService.update(id, request);
    }

    @DeleteMapping("/api/admin/theatres/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        theatreService.delete(id);
        return Map.of("message", "Theatre deleted");
    }
}
