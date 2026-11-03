package com.moviebooking.theatre.controller;

import com.moviebooking.theatre.dto.ScreenDto.ScreenRequest;
import com.moviebooking.theatre.dto.ScreenDto.ScreenResponse;
import com.moviebooking.theatre.service.ScreenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public screen listing (READ) and admin CRUD under /api/admin/screens. */
@RestController
public class ScreenController {

    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    @GetMapping("/api/screens")
    public List<ScreenResponse> list(@RequestParam(required = false) Long theatreId) {
        return screenService.list(theatreId);
    }

    @GetMapping("/api/screens/{id}")
    public ScreenResponse get(@PathVariable Long id) {
        return screenService.get(id);
    }

    @PostMapping("/api/admin/screens")
    public ResponseEntity<ScreenResponse> create(@Valid @RequestBody ScreenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(screenService.create(request));
    }

    @PutMapping("/api/admin/screens/{id}")
    public ScreenResponse update(@PathVariable Long id, @Valid @RequestBody ScreenRequest request) {
        return screenService.update(id, request);
    }

    @DeleteMapping("/api/admin/screens/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        screenService.delete(id);
        return Map.of("message", "Screen deleted");
    }
}
