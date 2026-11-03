package com.moviebooking.user.dto;

import com.moviebooking.user.entity.Admin;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs for user profile and admin-management endpoints. */
public final class UserDto {

    private UserDto() {
    }

    public record CustomerUpdateRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Size(max = 20) String phone,
            @Size(min = 8, max = 60) String newPassword) {
    }

    public record CreateAdminRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 60) String password,
            String permissionLevel) {
    }

    public record UpdateAdminRequest(@NotBlank String permissionLevel) {
    }

    /** Read-only view of a user. Hides the password hash (information hiding). */
    public record UserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String role,
            Integer loyaltyPoints,
            String permissionLevel,
            LocalDateTime createdAt) {

        public static UserResponse from(User user) {
            Integer points = user instanceof Customer c ? c.getLoyaltyPoints() : null;
            String level = user instanceof Admin a ? a.getPermissionLevel() : null;
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                    user.getRole().name(), points, level, user.getCreatedAt());
        }
    }
}
