package com.moviebooking.wallet.repository;

import com.moviebooking.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Data access for the wallet ledger. */
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    /**
     * The ledger belongs to the account, so it goes with it. Without this the foreign key
     * fk_wallet_customer would refuse to delete a customer who ever topped the wallet up.
     */
    void deleteByCustomerId(Long customerId);
}
