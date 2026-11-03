package com.moviebooking.user.service.auth;

import com.moviebooking.user.entity.User;
import com.moviebooking.user.entity.UserRole;

/**
 * Strategy interface for login.
 * ABSTRACTION: AuthService works with this interface and does not care which user type is logging in.
 * POLYMORPHISM: CustomerAuthStrategy and AdminAuthStrategy give different behaviour for the same call.
 */
public interface AuthStrategy {

    /** Checks email + password and returns the matching user, or throws BusinessException. */
    User authenticate(String email, String rawPassword);

    /** The role this login page is allowed to accept. */
    UserRole supportedRole();
}
