package com.moviebooking.payment.processor;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.payment.entity.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Card payment: 16-digit card number, 2% convenience fee. Only the last 4 digits are stored. */
@Component
public class CardPaymentProcessor extends AbstractPaymentProcessor {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.02");

    @Override
    public PaymentMethodType method() {
        return PaymentMethodType.CARD;
    }

    @Override
    public BigDecimal convenienceFee(BigDecimal bookingTotal) {
        return bookingTotal.multiply(FEE_RATE);
    }

    @Override
    public void validateReference(String reference) {
        if (reference == null || !reference.replace(" ", "").matches("\\d{16}")) {
            throw new BusinessException("Card number must be 16 digits");
        }
    }

    @Override
    public String maskReference(String reference) {
        String digits = reference.replace(" ", "");
        return "**** **** **** " + digits.substring(12);
    }

    @Override
    public String describe() {
        return "Card payment (2% convenience fee)";
    }
}
