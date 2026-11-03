package com.moviebooking.waitlist.service;

import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.notification.service.NotificationService;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import com.moviebooking.waitlist.dto.WaitlistDto.WaitlistResponse;
import com.moviebooking.waitlist.entity.WaitlistEntry;
import com.moviebooking.waitlist.repository.WaitlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The waitlist for sold-out showtimes. Joining is only allowed when the show really is full, so the list
 * means something; when a cancellation frees seats the first people on the list are notified, and they keep
 * a priority window before the freed seats are advertised again.
 */
@Service
@Transactional
public class WaitlistService {

    /** How long the people at the top of the list get before the seats are open to everyone again. */
    public static final int PRIORITY_MINUTES = 60;

    private final WaitlistRepository waitlist;
    private final BookingRepository bookings;
    private final ShowtimeRepository showtimes;
    private final CustomerRepository customers;
    private final NotificationService notifications;

    public WaitlistService(WaitlistRepository waitlist, BookingRepository bookings, ShowtimeRepository showtimes,
                           CustomerRepository customers, NotificationService notifications) {
        this.waitlist = waitlist;
        this.bookings = bookings;
        this.showtimes = showtimes;
        this.customers = customers;
        this.notifications = notifications;
    }

    /** CREATE: join the list. Refused when the show is not full or already started. */
    public WaitlistResponse join(Long customerId, Long showId) {
        Showtime show = showtimes.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + showId));
        if (!show.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("This showtime has already started");
        }
        if (freeSeats(show) > 0) {
            throw new BusinessException("Seats are available for this show - book them instead of waiting");
        }
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        WaitlistEntry entry = waitlist.findByShowIdAndCustomerId(showId, customerId)
                .orElseGet(() -> waitlist.save(new WaitlistEntry(show, customer)));
        return WaitlistResponse.from(entry, peopleAhead(showId, entry));
    }

    /** READ: the list of shows this customer is waiting for. */
    @Transactional(readOnly = true)
    public List<WaitlistResponse> mine(Long customerId) {
        return waitlist.findByCustomerIdOrderByJoinedAtDesc(customerId).stream()
                .map(e -> WaitlistResponse.from(e, peopleAhead(e.getShow().getId(), e)))
                .toList();
    }

    /** DELETE: leave the list. */
    public void leave(Long customerId, Long showId) {
        WaitlistEntry entry = waitlist.findByShowIdAndCustomerId(showId, customerId)
                .orElseThrow(() -> new BusinessException("You are not on the waitlist for this show"));
        waitlist.delete(entry);
    }

    /**
     * Called when seats are freed (a cancellation or a partial cancellation). Everyone waiting for that show
     * is told, in the order they joined, and the earliest ones are marked as notified so they can be seen at
     * the door as "you were told".
     */
    public int notifyFreedSeats(Showtime show, int freedSeats) {
        if (freedSeats <= 0) {
            return 0;
        }
        List<WaitlistEntry> waiting = waitlist.findByShowIdAndNotifiedAtIsNullOrderByJoinedAtAsc(show.getId());
        int told = 0;
        for (WaitlistEntry entry : waiting) {
            notifications.notifyWaitlist(entry.getCustomer(), show.getMovie().getTitle(),
                    show.getStartTime(), PRIORITY_MINUTES);
            entry.markNotified();
            told++;
        }
        return told;
    }

    private long peopleAhead(Long showId, WaitlistEntry mine) {
        return waitlist.findByShowIdAndNotifiedAtIsNullOrderByJoinedAtAsc(showId).stream()
                .filter(e -> e.getJoinedAt().isBefore(mine.getJoinedAt()))
                .count();
    }

    private long freeSeats(Showtime show) {
        long held = bookings.findHeldSeatCodes(show.getId(), -1L, BookingStatus.activeStatuses(),
                LocalDateTime.now()).size();
        return show.getScreen().getTotalSeats() - held;
    }
}
