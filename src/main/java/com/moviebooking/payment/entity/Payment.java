package com.moviebooking.payment.entity;

import com.moviebooking.booking.entity.Booking;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment record for a booking (the ledger entry).
 * ENCAPSULATION: refunds are applied only through refund(), which keeps the status consistent.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_method_paid", columnList = "method, paid_at"),
        @Index(name = "idx_payment_booking", columnList = "booking_id")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String transactionRef;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Total charged = booking total + convenience fee. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal convenienceFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;

    /** Masked payment reference (e.g. last 4 card digits). Full card numbers are never stored. */
    @Column(length = 40)
    private String maskedReference;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    protected Payment() {
        // required by JPA
    }

    public Payment(String transactionRef, Booking booking, BigDecimal amount, BigDecimal convenienceFee,
                   PaymentMethodType method, PaymentStatus status, String maskedReference) {
        this(transactionRef, booking, amount, convenienceFee, method, status, maskedReference, LocalDateTime.now());
    }

    /** Same payment, stamped with the moment it was taken (used when replaying past ticket sales). */
    public Payment(String transactionRef, Booking booking, BigDecimal amount, BigDecimal convenienceFee,
                   PaymentMethodType method, PaymentStatus status, String maskedReference, LocalDateTime paidAt) {
        this.transactionRef = transactionRef;
        this.booking = booking;
        this.amount = amount;
        this.convenienceFee = convenienceFee;
        this.method = method;
        this.status = status;
        this.maskedReference = maskedReference;
        this.paidAt = paidAt;
    }

    /**
     * Records a refund. The status becomes REFUNDED once the refunded total
     * reaches the booking total, otherwise PARTIALLY_REFUNDED.
     */
    public void refund(BigDecimal refundAmount, BigDecimal bookingTotal) {
        if (status == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("This payment has already been refunded");
        }
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        refundedAmount = refundedAmount.add(refundAmount);
        status = refundedAmount.compareTo(bookingTotal) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    /** Admin correction: set the status (used for manual refunds). */
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public Booking getBooking() {
        return booking;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getConvenienceFee() {
        return convenienceFee;
    }

    public PaymentMethodType getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getMaskedReference() {
        return maskedReference;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
