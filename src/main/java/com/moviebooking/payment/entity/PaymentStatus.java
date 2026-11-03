package com.moviebooking.payment.entity;

/** SUCCESS when paid; REFUNDED or PARTIALLY_REFUNDED after a cancellation or a manual refund. */
public enum PaymentStatus {
    SUCCESS,
    REFUNDED,
    PARTIALLY_REFUNDED
}
