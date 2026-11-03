package com.moviebooking.theatre.repository;

import com.moviebooking.theatre.entity.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for theatres. */
public interface TheatreRepository extends JpaRepository<Theatre, Long> {
}
