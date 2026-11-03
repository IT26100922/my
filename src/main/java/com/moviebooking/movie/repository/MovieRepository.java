package com.moviebooking.movie.repository;

import com.moviebooking.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Data access for movies. Works on the abstract Movie, so all subclasses are returned. */
public interface MovieRepository extends JpaRepository<Movie, Long> {

    boolean existsByTitleIgnoreCase(String title);

    /** Case-insensitive "contains" search. An empty string matches everything. */
    @Query("select m from Movie m where lower(m.title) like lower(concat('%', :title, '%')) "
            + "and lower(m.genre) like lower(concat('%', :genre, '%')) order by m.title asc")
    List<Movie> search(@Param("title") String title, @Param("genre") String genre);
}
