package com.moviebooking.notification.entity;

/** Kinds of in-app notification shown to a customer. */
public enum NotificationType {
    BOOKING_CONFIRMED,
    REMINDER_24H,
    REMINDER_2H,
    BOOKING_CANCELLED,
    BOOKING_MOVED,
    HOLD_EXPIRED,
    /** A seat was freed for a show the customer is waiting for. */
    WAITLIST_SEAT_FREE,
    /** Part of a booking was cancelled and the money went back to the wallet. */
    SEATS_RELEASED
}
