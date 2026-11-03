package com.moviebooking.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs used by the authentication endpoints. Records are immutable (encapsulated data). */
public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RegisterCustomerRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 60) String password,
            @Size(max = 20) String phone) {
    }

    public record SessionResponse(Long id, String fullName, String email, String role, Integer loyaltyPoints,
                                  String permissionLevel) {
    }
}
