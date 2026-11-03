package com.moviebooking.wallet.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.wallet.dto.WalletDto.TopUpRequest;
import com.moviebooking.wallet.dto.WalletDto.WalletResponse;
import com.moviebooking.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** The customer's wallet: balance, ledger and top-up. */
@RestController
@RequestMapping("/api/customer/wallet")
public class WalletController {

    private final WalletService wallet;

    public WalletController(WalletService wallet) {
        this.wallet = wallet;
    }

    @GetMapping
    public WalletResponse view(@SessionAttribute(SessionKeys.USER_ID) Long customerId) {
        return wallet.view(customerId);
    }

    @PostMapping("/top-up")
    public WalletResponse topUp(@SessionAttribute(SessionKeys.USER_ID) Long customerId,
                                @Valid @RequestBody TopUpRequest req) {
        return wallet.topUp(customerId, req);
    }
}
