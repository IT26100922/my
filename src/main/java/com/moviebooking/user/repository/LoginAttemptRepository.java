package com.moviebooking.user.repository;

import com.moviebooking.user.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for failed log-in counters (keyed by the hashed email). */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {
}
