package com.moviebooking.wallet.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import com.moviebooking.wallet.dto.WalletDto.TopUpRequest;
import com.moviebooking.wallet.dto.WalletDto.WalletEntry;
import com.moviebooking.wallet.dto.WalletDto.WalletResponse;
import com.moviebooking.wallet.entity.WalletEntryType;
import com.moviebooking.wallet.entity.WalletTransaction;
import com.moviebooking.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The wallet is a small ledger, not just a number: every top-up, payment and refund writes a row, and the
 * customer's balance is kept in step with those rows. A refund can therefore go back into the wallet at once.
 */
@Service
@Transactional
public class WalletService {

    private static final BigDecimal MIN_TOP_UP = new BigDecimal("50.00");
    private static final BigDecimal MAX_TOP_UP = new BigDecimal("50000.00");

    private final CustomerRepository customers;
    private final WalletTransactionRepository ledger;

    public WalletService(CustomerRepository customers, WalletTransactionRepository ledger) {
        this.customers = customers;
        this.ledger = ledger;
    }

    /** READ: the balance and the last movements. */
    @Transactional(readOnly = true)
    public WalletResponse view(Long customerId) {
        Customer customer = find(customerId);
        List<WalletEntry> entries = ledger.findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
                .map(WalletEntry::from).toList();
        return new WalletResponse(customer.getWalletBalance(), entries);
    }

    /** CREATE: money in. A demo top-up, so the customer does not need a card to try the wallet. */
    public WalletResponse topUp(Long customerId, TopUpRequest req) {
        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_TOP_UP) < 0) {
            throw new BusinessException("Top up at least LKR " + MIN_TOP_UP);
        }
        if (amount.compareTo(MAX_TOP_UP) > 0) {
            throw new BusinessException("A single top-up is limited to LKR " + MAX_TOP_UP);
        }
        Customer customer = find(customerId);
        customer.creditWallet(amount);
        ledger.save(new WalletTransaction(customer, WalletEntryType.TOP_UP, amount,
                customer.getWalletBalance(), "Top-up via " + (req.method() == null ? "CARD" : req.method())));
        return view(customerId);
    }

    /** Takes the money for a booking out of the wallet. Throws when the balance is too low. */
    public void debit(Long customerId, BigDecimal amount, String note) {
        Customer customer = find(customerId);
        try {
            customer.debitWallet(amount);
        } catch (IllegalStateException e) {
            throw new BusinessException("Not enough wallet balance: " + customer.getWalletBalance()
                    + " available, " + amount.setScale(2, RoundingMode.HALF_UP)
                    + " needed. Top up first or pay by card.");
        }
        ledger.save(new WalletTransaction(customer, WalletEntryType.PAYMENT,
                amount.setScale(2, RoundingMode.HALF_UP).negate(), customer.getWalletBalance(), note));
    }

    /** Pays a refund straight back into the wallet. */
    public void credit(Long customerId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        Customer customer = find(customerId);
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        customer.creditWallet(rounded);
        ledger.save(new WalletTransaction(customer, WalletEntryType.REFUND, rounded,
                customer.getWalletBalance(), note));
    }

    private Customer find(Long customerId) {
        return customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
