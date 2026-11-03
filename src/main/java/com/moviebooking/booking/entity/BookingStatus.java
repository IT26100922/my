package com.moviebooking.booking.entity;

import java.util.List;

/**
 * Lifecycle of a booking:
 * PENDING_PAYMENT -> CONFIRMED -> CHECKED_IN (the ticket was scanned at the door),
 * or -> CANCELLED at any time after booking.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    /** The ticket was scanned at the door and the customer went in. */
    CHECKED_IN,
    CANCELLED;

    /** Statuses that hold seats (a cancelled booking releases its seats). */
    public static List<BookingStatus> activeStatuses() {
        return List.of(PENDING_PAYMENT, CONFIRMED, CHECKED_IN);
    }

    /** Statuses that count as a paid, valid ticket. */
    public static List<BookingStatus> paidStatuses() {
        return List.of(CONFIRMED, CHECKED_IN);
    }
}
