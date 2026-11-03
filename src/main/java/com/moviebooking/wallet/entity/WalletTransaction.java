package com.moviebooking.wallet.entity;

import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of the wallet ledger. The balance on Customer is the running total; this table explains it,
 * so a customer can always see where the money came from and where it went.
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_customer_created", columnList = "customer_id, created_at")
})
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WalletEntryType type;

    /** Positive when money comes in, negative when it goes out. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** The balance after this entry, so a row is readable on its own. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 120)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WalletTransaction() {
        // required by JPA
    }

    public WalletTransaction(Customer customer, WalletEntryType type, BigDecimal amount,
                             BigDecimal balanceAfter, String note) {
        this.customer = customer;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public WalletEntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
