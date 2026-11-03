package com.moviebooking.theatre.dto;

import com.moviebooking.theatre.entity.Theatre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs for theatres and screens. */
public final class TheatreDto {

    private TheatreDto() {
    }

    public record TheatreRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 60) String city,
            @Size(max = 200) String address,
            @Size(max = 20) String phone) {
    }

    public record TheatreResponse(Long id, String name, String city, String address, String phone) {

        public static TheatreResponse from(Theatre t) {
            return new TheatreResponse(t.getId(), t.getName(), t.getCity(), t.getAddress(), t.getPhone());
        }
    }
}
