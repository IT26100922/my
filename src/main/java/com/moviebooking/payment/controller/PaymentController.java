package com.moviebooking.payment.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.payment.dto.PaymentDto.PaymentRequest;
import com.moviebooking.payment.dto.PaymentDto.PaymentResponse;
import com.moviebooking.payment.dto.PaymentDto.PaymentStatusRequest;
import com.moviebooking.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Customer checkout and history (/api/customer/payments) plus admin payment management (/api/admin/payments). */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/customer/payments")
    public ResponseEntity<PaymentResponse> pay(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(userId, request));
    }

    @GetMapping("/api/customer/payments")
    public List<PaymentResponse> listMine(@SessionAttribute(SessionKeys.USER_ID) Long userId) {
        return paymentService.listMine(userId);
    }

    @GetMapping("/api/admin/payments")
    public List<PaymentResponse> listAll() {
        return paymentService.listAll();
    }

    @PutMapping("/api/admin/payments/{id}")
    public PaymentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody PaymentStatusRequest request) {
        return paymentService.updateStatus(id, request);
    }

    @DeleteMapping("/api/admin/payments/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return Map.of("message", "Refunded payment removed from ledger");
    }
}
