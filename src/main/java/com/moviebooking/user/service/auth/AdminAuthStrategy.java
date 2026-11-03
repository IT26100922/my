package com.moviebooking.user.service.auth;

import com.moviebooking.user.entity.UserRole;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Login rules for the admin portal: only ADMIN accounts may sign in here. */
@Component
public class AdminAuthStrategy extends AbstractAuthStrategy {

    public AdminAuthStrategy(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts) {
        super(users, encoder, attempts);
    }

    @Override
    public UserRole supportedRole() {
        return UserRole.ADMIN;
    }
}
