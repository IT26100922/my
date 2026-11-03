package com.moviebooking.payment.entity;

/** Payment methods supported at checkout. Each one has its own processor class. */
public enum PaymentMethodType {
    CARD,
    WALLET,
    CASH_COUNTER
}
