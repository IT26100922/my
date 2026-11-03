package com.moviebooking.promo.controller;

import com.moviebooking.promo.dto.PromoDto.PromoCheckRequest;
import com.moviebooking.promo.dto.PromoDto.PromoCheckResponse;
import com.moviebooking.promo.dto.PromoDto.PromoRequest;
import com.moviebooking.promo.dto.PromoDto.PromoResponse;
import com.moviebooking.promo.service.PromoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin CRUD for promo codes, plus the customer check the checkout page calls. */
@RestController
public class PromoController {

    private final PromoService promos;

    public PromoController(PromoService promos) {
        this.promos = promos;
    }

    @GetMapping("/api/admin/promos")
    public List<PromoResponse> list() {
        return promos.list();
    }

    @PostMapping("/api/admin/promos")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoResponse create(@Valid @RequestBody PromoRequest req) {
        return promos.create(req);
    }

    @PutMapping("/api/admin/promos/{id}")
    public PromoResponse update(@PathVariable Long id, @Valid @RequestBody PromoRequest req) {
        return promos.update(id, req);
    }

    @DeleteMapping("/api/admin/promos/{id}")
    public void delete(@PathVariable Long id) {
        promos.delete(id);
    }

    /** READ (customer): does this code apply to a basket of this value? */
    @PostMapping("/api/customer/promo/check")
    public PromoCheckResponse check(@Valid @RequestBody PromoCheckRequest req) {
        return promos.check(req);
    }
}
