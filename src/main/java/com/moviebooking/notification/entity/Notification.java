package com.moviebooking.notification.entity;

import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An in-app message for a customer (booking confirmed, show reminder, hold expired...).
 * The booking is stored as a plain id and reference, not as a foreign key, so the history
 * survives when a booking row is later deleted.
 * ENCAPSULATION: the read state changes only through markRead().
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "idx_notification_unread", columnList = "customer_id, read_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "booking_reference", length = 20)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    protected Notification() {
        // required by JPA
    }

    public Notification(Customer customer, Long bookingId, String bookingReference,
                        NotificationType type, String message) {
        this.customer = customer;
        this.bookingId = bookingId;
        this.bookingReference = bookingReference;
        this.type = type;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Long getBookingId() { return bookingId; }
    public String getBookingReference() { return bookingReference; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
}
