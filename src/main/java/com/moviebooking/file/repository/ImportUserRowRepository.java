package com.moviebooking.file.repository;

import com.moviebooking.file.entity.ImportUserRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** MySQL staging table for the user import. */
public interface ImportUserRowRepository extends JpaRepository<ImportUserRow, Long> {

    List<ImportUserRow> findAllByOrderByIdAsc();
}
