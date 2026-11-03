package com.moviebooking.payment.processor;

import com.moviebooking.payment.entity.PaymentMethodType;

import java.math.BigDecimal;

/**
 * STRATEGY interface for payment methods.
 * ABSTRACTION: PaymentService charges any method through this interface.
 * POLYMORPHISM: card, wallet and cash counter each validate and charge differently.
 */
public interface PaymentProcessor {

    PaymentMethodType method();

    /** Convenience fee added to the booking total for this method. */
    BigDecimal convenienceFee(BigDecimal bookingTotal);

    /** Throws BusinessException when the reference supplied by the customer is invalid. */
    void validateReference(String reference);

    /** Returns a safe version of the reference that can be stored and displayed. */
    String maskReference(String reference);

    /** Text shown on the receipt. */
    String describe();

    /** Charges the amount (template method implemented by AbstractPaymentProcessor). */
    PaymentCharge charge(BigDecimal bookingTotal, String reference);
}
