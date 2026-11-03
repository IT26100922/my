package com.moviebooking.user.service.auth;

import com.moviebooking.exception.TooManyAttemptsException;
import com.moviebooking.user.entity.LoginAttempt;
import com.moviebooking.user.repository.LoginAttemptRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Slows down password guessing with two counters, both stored in the database:
 * <ul>
 *   <li><b>Per email:</b> {@value #MAX_FAILURES} failed log-ins within {@link #WINDOW} lock that email for {@link #LOCK}.
 *       Stops guessing against one account.</li>
 *   <li><b>Per IP address:</b> {@value #IP_MAX_FAILURES} failed log-ins within {@link #WINDOW} lock that address for
 *       {@link #LOCK}. Stops one machine from locking out many accounts. The limit is higher, because several people
 *       on one network can share an address.</li>
 * </ul>
 * Failures are counted for unknown emails too, so the lock does not reveal which emails have accounts.
 * A successful log-in clears the email counter only.
 */
@Service
@Transactional
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final int IP_MAX_FAILURES = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);

    private final LoginAttemptRepository attempts;

    public LoginAttemptService(LoginAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** Throws TooManyAttemptsException while the email or the client address is locked. */
    @Transactional(readOnly = true)
    public void assertNotLocked(String email) {
        LocalDateTime now = LocalDateTime.now();
        assertNotLocked(emailKey(email), now);
        clientIp().ifPresent(ip -> assertNotLocked(ipKey(ip), now));
    }

    /** Counts one failed log-in against the email and the client address. */
    public void recordFailure(String email) {
        LocalDateTime now = LocalDateTime.now();
        bump(emailKey(email), now, MAX_FAILURES);
        clientIp().ifPresent(ip -> bump(ipKey(ip), now, IP_MAX_FAILURES));
    }

    /** Clears the email counter after a successful log-in. The address counter is kept. */
    public void recordSuccess(String email) {
        attempts.deleteById(emailKey(email));
    }

    private void assertNotLocked(String key, LocalDateTime now) {
        attempts.findById(key)
                .map(LoginAttempt::getLockedUntil)
                .filter(now::isBefore)
                .ifPresent(until -> {
                    long minutes = Duration.between(now, until).toMinutes() + 1;
                    throw new TooManyAttemptsException(
                            "Too many failed log-in attempts. Try again in " + minutes + " minute(s).");
                });
    }

    private void bump(String key, LocalDateTime now, int max) {
        LoginAttempt a = attempts.findById(key).orElseGet(() -> new LoginAttempt(key));
        a.recordFailure(now, WINDOW, LOCK, max);
        attempts.save(a);
    }

    /** The address of the request being handled, if there is one (absent outside web requests). */
    private static Optional<String> clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return Optional.ofNullable(request.getRemoteAddr()).filter(s -> !s.isBlank());
        }
        return Optional.empty();
    }

    /** Storage key for an email counter (the email itself is never stored). */
    static String emailKey(String email) {
        return hash("email:" + (email == null ? "" : email.trim().toLowerCase()));
    }

    private static String ipKey(String ip) {
        return hash("ip:" + ip);
    }

    /** SHA-256 hex, so the table never holds readable emails or addresses. */
    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
