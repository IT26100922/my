package com.moviebooking.file.repository;

import com.moviebooking.file.entity.ImportShowtimeRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** MySQL staging table for the showtime import. */
public interface ImportShowtimeRowRepository extends JpaRepository<ImportShowtimeRow, Long> {

    List<ImportShowtimeRow> findAllByOrderByIdAsc();
}
