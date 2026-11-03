package com.moviebooking.file.controller;

import com.moviebooking.file.dto.FileDto.ImportResult;
import com.moviebooking.file.dto.FileDto.RefreshResult;
import com.moviebooking.file.dto.FileDto.StagingStatus;
import com.moviebooking.file.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * Admin import tools. Every import reads a MySQL staging table and writes the live table -
 * no text file is read. The only file written here is the timestamped sales report.
 */
@RestController
@RequestMapping("/api/admin/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** Row counts for import_movies / import_users / import_showtimes and their live tables. */
    @GetMapping("/status")
    public StagingStatus status() {
        return fileService.status();
    }

    @PostMapping("/import-movies")
    public ImportResult importMovies() {
        return fileService.importMovies();
    }

    @PostMapping("/import-users")
    public ImportResult importUsers() {
        return fileService.importUsers();
    }

    @PostMapping("/import-showtimes")
    public ImportResult importShowtimes() {
        return fileService.importShowtimes();
    }

    /** Copies the movies table back into import_movies (a fresh staging snapshot). */
    @PostMapping("/export-movies")
    public RefreshResult exportMovies() {
        return new RefreshResult("import_movies", fileService.exportMovies());
    }

    /** Copies the upcoming showtimes back into import_showtimes. */
    @PostMapping("/export-showtimes")
    public RefreshResult exportShowtimes() {
        return new RefreshResult("import_showtimes", fileService.exportShowtimes());
    }

    @GetMapping("/export-sales")
    public ResponseEntity<Resource> exportSales() {
        Path report = fileService.exportSalesReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new FileSystemResource(report));
    }
}
