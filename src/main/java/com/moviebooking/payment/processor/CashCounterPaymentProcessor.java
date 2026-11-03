package com.moviebooking.payment.processor;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.payment.entity.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Pay at the cinema counter. The customer enters the counter code (e.g. CNT-01). No fee. */
@Component
public class CashCounterPaymentProcessor extends AbstractPaymentProcessor {

    @Override
    public PaymentMethodType method() {
        return PaymentMethodType.CASH_COUNTER;
    }

    @Override
    public BigDecimal convenienceFee(BigDecimal bookingTotal) {
        return BigDecimal.ZERO;
    }

    @Override
    public void validateReference(String reference) {
        if (reference == null || !reference.trim().matches("^CNT-\\d{2}$")) {
            throw new BusinessException("Counter code must look like CNT-01");
        }
    }

    @Override
    public String maskReference(String reference) {
        return reference.trim();
    }

    @Override
    public String describe() {
        return "Pay at cinema counter";
    }
}
