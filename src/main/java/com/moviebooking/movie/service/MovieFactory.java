package com.moviebooking.movie.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.movie.entity.ImaxMovie;
import com.moviebooking.movie.entity.Movie;
import com.moviebooking.movie.entity.StandardMovie;
import com.moviebooking.movie.entity.ThreeDMovie;
import org.springframework.stereotype.Component;

/**
 * FACTORY: chooses the correct Movie subclass from the format code sent by the UI.
 * This keeps "new XxxMovie()" out of the service and controller code.
 */
@Component
public class MovieFactory {

    public Movie create(String format) {
        String code = format == null ? "" : format.trim().toUpperCase();
        return switch (code) {
            case "STANDARD" -> new StandardMovie();
            case "IMAX" -> new ImaxMovie();
            case "THREE_D" -> new ThreeDMovie();
            default -> throw new BusinessException("Unknown movie format: " + format
                    + " (use STANDARD, IMAX or THREE_D)");
        };
    }
}
