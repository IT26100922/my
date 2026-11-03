package com.moviebooking.payment.repository;

import com.moviebooking.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Data access for payments. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    List<Payment> findByBookingCustomerIdOrderByPaidAtDesc(Long customerId);

    List<Payment> findAllByOrderByPaidAtDesc();

    /** Revenue per film: [title, net seat money, bookings, seats] — one query, no N+1. */
    @Query("select b.show.movie.title, sum(p.amount - p.convenienceFee), count(distinct b.id), "
            + "sum(size(b.seatCodes)) "
            + "from Payment p join p.booking b "
            + "where p.status = com.moviebooking.payment.entity.PaymentStatus.SUCCESS "
            + "and b.status in :paidStatuses and p.paidAt >= :since "
            + "group by b.show.movie.title order by sum(p.amount - p.convenienceFee) desc")
    List<Object[]> revenueByMovie(@Param("paidStatuses") List<com.moviebooking.booking.entity.BookingStatus> paidStatuses,
                                  @Param("since") LocalDateTime since);

    /** Revenue per screen: [theatre · screen, net seat money, bookings]. */
    @Query("select concat(b.show.screen.theatre.name, ' · ', b.show.screen.name), "
            + "sum(p.amount - p.convenienceFee), count(distinct b.id) "
            + "from Payment p join p.booking b "
            + "where p.status = com.moviebooking.payment.entity.PaymentStatus.SUCCESS "
            + "and b.status in :paidStatuses and p.paidAt >= :since "
            + "group by b.show.screen.theatre.name, b.show.screen.name "
            + "order by sum(p.amount - p.convenienceFee) desc")
    List<Object[]> revenueByScreen(@Param("paidStatuses") List<com.moviebooking.booking.entity.BookingStatus> paidStatuses,
                                   @Param("since") LocalDateTime since);

    /** Revenue per day: [date, net seat money]. */
    @Query("select function('date', p.paidAt), sum(p.amount - p.convenienceFee) "
            + "from Payment p join p.booking b "
            + "where p.status = com.moviebooking.payment.entity.PaymentStatus.SUCCESS "
            + "and b.status in :paidStatuses and p.paidAt >= :since "
            + "group by function('date', p.paidAt) order by function('date', p.paidAt)")
    List<Object[]> revenueByDay(@Param("paidStatuses") List<com.moviebooking.booking.entity.BookingStatus> paidStatuses,
                                @Param("since") LocalDateTime since);

    /** Which payment methods people actually use: [method, count, revenue]. */
    @Query("select p.method, count(p), sum(p.amount) from Payment p "
            + "where p.status = com.moviebooking.payment.entity.PaymentStatus.SUCCESS and p.paidAt >= :since "
            + "group by p.method order by count(p) desc")
    List<Object[]> methodMix(@Param("since") LocalDateTime since);
}
