package com.moviebooking.theatre.dto;

import com.moviebooking.theatre.entity.Screen;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** DTOs for screens. */
public final class ScreenDto {

    private ScreenDto() {
    }

    public record ScreenRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Long theatreId,
            @NotBlank String screenType,
            @NotNull @Min(1) @Max(26) Integer rowCount,
            @NotNull @Min(1) @Max(30) Integer seatsPerRow) {
    }

    public record ScreenResponse(
            Long id,
            String name,
            Long theatreId,
            String theatreName,
            String screenType,
            String category,
            Integer rowCount,
            Integer seatsPerRow,
            Integer totalSeats,
            BigDecimal priceMultiplier) {

        public static ScreenResponse from(Screen s) {
            return new ScreenResponse(s.getId(), s.getName(), s.getTheatre().getId(), s.getTheatre().getName(),
                    s.getScreenType(), s.getCategory(), s.getRowCount(), s.getSeatsPerRow(),
                    s.getTotalSeats(), s.getPriceMultiplier());
        }
    }
}
