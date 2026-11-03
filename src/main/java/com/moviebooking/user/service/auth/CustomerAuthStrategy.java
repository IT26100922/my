package com.moviebooking.user.service.auth;

import com.moviebooking.user.entity.UserRole;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Login rules for the customer portal: only CUSTOMER accounts may sign in here. */
@Component
public class CustomerAuthStrategy extends AbstractAuthStrategy {

    public CustomerAuthStrategy(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts) {
        super(users, encoder, attempts);
    }

    @Override
    public UserRole supportedRole() {
        return UserRole.CUSTOMER;
    }
}
