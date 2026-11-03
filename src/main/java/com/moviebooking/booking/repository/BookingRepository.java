package com.moviebooking.booking.repository;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for bookings.
 *
 * <p>The list queries below carry an {@code @EntityGraph}: a booking is always shown with its show, film,
 * screen, theatre and customer, so fetching those in the same query removes the N+1 problem that a naive
 * "for each booking, load the film" loop creates. The seat set is part of the graph too, because a booking
 * is almost never displayed without its seats.</p>
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "customer",
            "seatCodes"})
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "customer",
            "seatCodes"})
    List<Booking> findAllByOrderByCreatedAtDesc();

    /** Same list, one page at a time, for the admin table. */
    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "customer",
            "seatCodes"})
    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "customer",
            "seatCodes"})
    Optional<Booking> findByReference(String reference);

    boolean existsByCustomerId(Long customerId);

    boolean existsByShowId(Long showId);

    /** Bookings in a given status whose show starts inside (from, to]. Used by the reminder job. */
    @Query("select b from Booking b where b.status = :status "
            + "and b.show.startTime > :from and b.show.startTime <= :to")
    List<Booking> findStartingBetween(@Param("status") BookingStatus status,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /**
     * Seats already held for a showtime by bookings in the given statuses.
     * excludeBookingId lets an edit ignore the booking being changed (pass -1 otherwise).
     */
    @Query("select seat from Booking b join b.seatCodes seat "
            + "where b.show.id = :showId and b.id <> :excludeBookingId and b.status in :statuses "
            + "and (b.expiresAt is null or b.expiresAt > :now)")
    List<String> findHeldSeatCodes(@Param("showId") Long showId,
                                   @Param("excludeBookingId") Long excludeBookingId,
                                   @Param("statuses") Collection<BookingStatus> statuses,
                                   @Param("now") LocalDateTime now);

    /** Unpaid bookings whose seat hold has run out (released by the hold sweeper). */
    @Query("select b from Booking b where b.status = :status and b.expiresAt <= :now")
    List<Booking> findExpiredHolds(@Param("status") BookingStatus status,
                                   @Param("now") LocalDateTime now);

    /** Paid tickets for shows on one day, used by the door check-in page. */
    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "customer",
            "seatCodes"})
    @Query("select b from Booking b where b.status in :statuses and b.show.startTime >= :from "
            + "and b.show.startTime < :to")
    List<Booking> findPaidForDay(@Param("statuses") Collection<BookingStatus> statuses,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /** Tickets scanned since a moment, newest first. */
    @Query("select b from Booking b where b.checkedInAt >= :from order by b.checkedInAt desc")
    List<Booking> findCheckedInSince(@Param("from") LocalDateTime from);

    /** Confirmed tickets for shows that already started and were never scanned (no-shows). */
    @Query("select count(b) from Booking b where b.status = com.moviebooking.booking.entity.BookingStatus.CONFIRMED "
            + "and b.show.startTime < :now and b.show.startTime >= :since")
    long countNoShows(@Param("now") LocalDateTime now, @Param("since") LocalDateTime since);

    /** How many tickets were sold for shows on a given day. */
    @Query("select count(b) from Booking b where b.status in :statuses "
            + "and b.show.startTime >= :from and b.show.startTime < :to")
    long countTicketsOnDay(@Param("statuses") Collection<BookingStatus> statuses,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Bookings created since a moment (the top of the funnel). */
    @Query("select count(b) from Booking b where b.createdAt >= :since")
    long countCreatedSince(@Param("since") LocalDateTime since);

    /** Bookings in the given statuses created since a moment. */
    @Query("select count(b) from Booking b where b.status in :statuses and b.createdAt >= :since")
    long countByStatusSince(@Param("statuses") Collection<BookingStatus> statuses,
                            @Param("since") LocalDateTime since);

    /** Seats sold grouped by the hour the show starts: [hour, seats]. */
    @Query("select hour(b.show.startTime), sum(size(b.seatCodes)) from Booking b "
            + "where b.status in :statuses and b.createdAt >= :since "
            + "group by hour(b.show.startTime) order by hour(b.show.startTime)")
    List<Object[]> seatsByStartHour(@Param("statuses") Collection<BookingStatus> statuses,
                                    @Param("since") LocalDateTime since);

    /** Bookings whose show was on a given day, for the attendance figure. */
    @Query("select count(b), coalesce(sum(size(b.seatCodes)), 0) from Booking b where b.status in :statuses "
            + "and b.show.startTime >= :from and b.show.startTime < :to")
    Object[] seatTotalsOnDay(@Param("statuses") Collection<BookingStatus> statuses,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** How many bookings belong to a show that has already started (used to decide if history exists). */
    @Query("select count(b) from Booking b where b.show.startTime < :now")
    long countForPastShows(@Param("now") LocalDateTime now);
}
