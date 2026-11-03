package com.moviebooking.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Failed log-in counter for one email address, kept in the database so a restart does not clear a lock.
 * The key is a SHA-256 hash of the email, so unknown addresses are not stored in readable form.
 * ENCAPSULATION: the counters change only through recordFailure().
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(nullable = false)
    private int failures;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    protected LoginAttempt() {
        // required by JPA
    }

    public LoginAttempt(String emailHash) {
        this.emailHash = emailHash;
    }

    /**
     * Counts one failed log-in. A new window starts when the previous one has expired or a lock has ended.
     * Reaching maxFailures inside the window locks the address for the lock duration.
     */
    public void recordFailure(LocalDateTime now, Duration window, Duration lock, int maxFailures) {
        boolean fresh = windowStart == null
                || windowStart.plus(window).isBefore(now)
                || (lockedUntil != null && !now.isBefore(lockedUntil));
        windowStart = fresh ? now : windowStart;
        failures = fresh ? 1 : failures + 1;
        lockedUntil = failures >= maxFailures ? now.plus(lock) : null;
    }

    /** The lock end time, or null when the address is not locked. */
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public int getFailures() {
        return failures;
    }
}
