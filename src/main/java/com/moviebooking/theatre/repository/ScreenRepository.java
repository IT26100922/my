package com.moviebooking.theatre.repository;

import com.moviebooking.theatre.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Data access for screens (abstract Screen, so every subclass is returned). */
public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByTheatreIdOrderByNameAsc(Long theatreId);

    boolean existsByTheatreId(Long theatreId);
}
