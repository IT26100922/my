package com.moviebooking.user.service.auth;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.user.entity.User;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Template-method base class for login strategies.
 * ABSTRACTION: the shared login steps are written once here.
 * The subclass only states which role it accepts (supportedRole).
 */
public abstract class AbstractAuthStrategy implements AuthStrategy {

    protected static final String INVALID_LOGIN = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final LoginAttemptService attempts;

    protected AbstractAuthStrategy(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts) {
        this.users = users;
        this.encoder = encoder;
        this.attempts = attempts;
    }

    @Override
    public final User authenticate(String email, String rawPassword) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        attempts.assertNotLocked(normalised);

        // POLYMORPHISM: user.getRole() is decided by the concrete subclass (Customer / Admin).
        User user = users.findByEmailIgnoreCase(normalised).orElse(null);
        boolean ok = user != null
                && user.getRole() == supportedRole()
                && rawPassword != null
                && encoder.matches(rawPassword, user.getPasswordHash());
        if (!ok) {
            attempts.recordFailure(normalised);
            throw new BusinessException(INVALID_LOGIN);
        }
        attempts.recordSuccess(normalised);
        return user;
    }
}
