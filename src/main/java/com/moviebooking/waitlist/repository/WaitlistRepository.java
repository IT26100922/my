package com.moviebooking.waitlist.repository;

import com.moviebooking.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data access for the waitlist. */
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByShowIdAndCustomerId(Long showId, Long customerId);

    List<WaitlistEntry> findByCustomerIdOrderByJoinedAtDesc(Long customerId);

    /** Everyone waiting for one show, first come first served. */
    List<WaitlistEntry> findByShowIdAndNotifiedAtIsNullOrderByJoinedAtAsc(Long showId);

    long countByShowId(Long showId);

    /** Leaving every list at once, used when the account itself is deleted. */
    void deleteByCustomerId(Long customerId);
}
