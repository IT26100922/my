package com.moviebooking.waitlist.entity;

import com.moviebooking.show.entity.Showtime;
import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * "Tell me when a seat frees up." One row per customer per sold-out showtime. When a cancellation releases
 * seats, the people on the list are told in the order they joined, and the first of them gets a short
 * priority window before the seats are open to everyone again.
 */
@Entity
@Table(name = "waitlist_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_waitlist_customer_show", columnNames = {"show_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_waitlist_show_joined", columnList = "show_id, joined_at")
})
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Showtime show;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** Set once the customer has been told that seats are free. */
    private LocalDateTime notifiedAt;

    protected WaitlistEntry() {
        // required by JPA
    }

    public WaitlistEntry(Showtime show, Customer customer) {
        this.show = show;
        this.customer = customer;
        this.joinedAt = LocalDateTime.now();
    }

    public void markNotified() {
        this.notifiedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Showtime getShow() {
        return show;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }
}
