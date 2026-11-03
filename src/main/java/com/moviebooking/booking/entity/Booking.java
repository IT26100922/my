package com.moviebooking.booking.entity;

import com.moviebooking.show.entity.Showtime;
import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A customer's reservation of one or more seats for one showtime.
 * ENCAPSULATION: the seat set is exposed read-only; changes go through replaceSeats() and the status methods.
 */
@Entity
@Table(name = "bookings", indexes = {
        // the admin table pages by created_at, the seat check filters by show, the door scan by reference
        @Index(name = "idx_booking_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "idx_booking_show_status", columnList = "show_id, status"),
        @Index(name = "idx_booking_reference", columnList = "reference"),
        @Index(name = "idx_booking_checked_in", columnList = "checked_in_at")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Showtime show;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_code", nullable = false, length = 6)
    private Set<String> seatCodes = new HashSet<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Money taken off by a promo code. Zero when no code was used. */
    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(length = 30)
    private String promoCode;

    /** Set when the ticket is scanned at the door. */
    private LocalDateTime checkedInAt;

    @Column(length = 60)
    private String checkedInBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** End of the seat hold while the booking awaits payment. Null once confirmed. */
    private LocalDateTime expiresAt;

    protected Booking() {
        // required by JPA
    }

    public Booking(String reference, Customer customer, Showtime show, Set<String> seats, BigDecimal totalAmount,
                   LocalDateTime expiresAt) {
        this.reference = reference;
        this.customer = customer;
        this.show = show;
        this.seatCodes = new HashSet<>(seats);
        this.totalAmount = totalAmount;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.discountAmount = BigDecimal.ZERO.setScale(2);
    }

    /** True when an unpaid booking has run past its seat hold. */
    public boolean isHoldExpired(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT && expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Replaces the seats (only used while the booking is still awaiting payment). */
    public void replaceSeats(Set<String> seats, BigDecimal newTotal) {
        seatCodes.clear();
        seatCodes.addAll(seats);
        this.totalAmount = newTotal;
    }

    /** Moves a confirmed booking to another showtime with new seats and the new total. */
    public void moveTo(Showtime newShow, Set<String> seats, BigDecimal newTotal) {
        this.show = newShow;
        this.seatCodes.clear();
        this.seatCodes.addAll(seats);
        this.totalAmount = newTotal;
    }

    public void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
        this.expiresAt = null;
    }

    public void markCancelled() {
        this.status = BookingStatus.CANCELLED;
    }

    /**
     * Records a promo discount. Called once, while the seats are being reserved, and the amount is stored on
     * the booking so a later refund is worked out on what the customer actually paid.
     */
    public void applyDiscount(String code, BigDecimal discount) {
        this.promoCode = code;
        this.discountAmount = discount == null ? BigDecimal.ZERO.setScale(2) : discount.setScale(2);
    }

    /**
     * Keeps only the given seats and re-prices the booking. Used when a customer gives part of a booking
     * back; the money for the removed seats is refunded by the service before this is called.
     */
    public void keepOnly(Set<String> seatsToKeep, BigDecimal newTotal) {
        seatCodes.retainAll(seatsToKeep);
        this.totalAmount = newTotal.setScale(2);
    }

    /** Marks the ticket as used at the door. Only a confirmed booking can be checked in. */
    public void checkIn(String staff) {
        recordCheckIn(LocalDateTime.now(), staff);
    }

    /**
     * Records a door scan at a given moment. A live scan passes the current time; the demo-history seeder
     * passes a past moment so a month of attendance can be rebuilt.
     */
    public void recordCheckIn(LocalDateTime at, String staff) {
        if (status == BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("This ticket has already been scanned");
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed booking can be checked in");
        }
        this.status = BookingStatus.CHECKED_IN;
        this.checkedInAt = at;
        this.checkedInBy = staff;
    }

    /** Re-dates the booking; only used when replaying past sales for the demo history. */
    public void backdateCreatedAt(LocalDateTime at) {
        if (at != null) {
            this.createdAt = at;
        }
    }

    public int getSeatCount() {
        return seatCodes.size();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Showtime getShow() {
        return show;
    }

    /** Read-only view (information hiding). */
    public Set<String> getSeatCodes() {
        return Collections.unmodifiableSet(seatCodes);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount == null ? BigDecimal.ZERO.setScale(2) : discountAmount;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public String getCheckedInBy() {
        return checkedInBy;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
