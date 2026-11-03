package com.moviebooking.file.repository;

import com.moviebooking.file.entity.ImportMovieRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** MySQL staging table for the movie import. */
public interface ImportMovieRowRepository extends JpaRepository<ImportMovieRow, Long> {

    List<ImportMovieRow> findAllByOrderByIdAsc();

    boolean existsByTitle(String title);
}
