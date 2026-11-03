package com.moviebooking.payment.processor;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.payment.entity.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Mobile wallet payment (phone number or wallet ID). No fee. */
@Component
public class WalletPaymentProcessor extends AbstractPaymentProcessor {

    @Override
    public PaymentMethodType method() {
        return PaymentMethodType.WALLET;
    }

    @Override
    public BigDecimal convenienceFee(BigDecimal bookingTotal) {
        return BigDecimal.ZERO;
    }

    @Override
    public void validateReference(String reference) {
        if (reference == null || !reference.trim().matches("^[A-Za-z0-9@._+-]{5,40}$")) {
            throw new BusinessException("Wallet ID must be 5-40 characters (phone number or email)");
        }
    }

    @Override
    public String maskReference(String reference) {
        String value = reference.trim();
        return value.substring(0, 2) + "******" + value.substring(value.length() - 2);
    }

    @Override
    public String describe() {
        return "Mobile wallet payment";
    }
}
