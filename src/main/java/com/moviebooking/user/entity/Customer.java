package com.moviebooking.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A customer who browses movies and books tickets.
 * INHERITANCE: extends User and adds loyalty points.
 */
@Entity
@DiscriminatorValue("CUSTOMER")
public class Customer extends User {

    @Column(name = "loyalty_points")
    private Integer loyaltyPoints;

    /** Money the customer can spend instead of a card. Refunds can be paid back into it instantly. */
    @Column(name = "wallet_balance", precision = 10, scale = 2)
    private BigDecimal walletBalance;

    protected Customer() {
        // required by JPA
    }

    public Customer(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone);
        this.loyaltyPoints = 0;
        this.walletBalance = BigDecimal.ZERO;
    }

    @Override
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints == null ? 0 : loyaltyPoints;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance == null ? BigDecimal.ZERO : walletBalance.setScale(2, RoundingMode.HALF_UP);
    }

    /** ENCAPSULATION: the balance only moves through these two methods, never by assignment. */
    public void debitWallet(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (getWalletBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Not enough wallet balance");
        }
        this.walletBalance = getWalletBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void creditWallet(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        this.walletBalance = getWalletBalance().add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Business rule: customers earn 1 loyalty point per seat when a payment succeeds. */
    public void addLoyaltyPoints(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Loyalty points cannot be negative");
        }
        this.loyaltyPoints = getLoyaltyPoints() + points;
    }
}
