package com.moviebooking.promo.service;

import com.moviebooking.booking.dto.BookingDto.PromoQuote;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.promo.dto.PromoDto.PromoCheckRequest;
import com.moviebooking.promo.dto.PromoDto.PromoCheckResponse;
import com.moviebooking.promo.dto.PromoDto.PromoRequest;
import com.moviebooking.promo.dto.PromoDto.PromoResponse;
import com.moviebooking.promo.entity.PromoCode;
import com.moviebooking.promo.entity.PromoType;
import com.moviebooking.promo.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Promo codes as a pricing strategy: the customer checks a code before paying, the booking stores the
 * discount, and the use is counted so a code with a limit really stops working.
 */
@Service
@Transactional
public class PromoService {

    private final PromoCodeRepository promos;

    public PromoService(PromoCodeRepository promos) {
        this.promos = promos;
    }

    /** READ (admin): every code, alphabetically. */
    @Transactional(readOnly = true)
    public List<PromoResponse> list() {
        return promos.findAllByOrderByCodeAsc().stream().map(PromoResponse::from).toList();
    }

    /** CREATE (admin). */
    public PromoResponse create(PromoRequest req) {
        String code = PromoCode.normalize(req.code());
        if (promos.existsByCode(code)) {
            throw new BusinessException("A code with that name already exists");
        }
        PromoCode promo = new PromoCode(code, parseType(req.type()), req.value(), req.minSpend(),
                req.validFrom(), req.validUntil(), req.maxUses(), req.description());
        return PromoResponse.from(promos.save(promo));
    }

    /** UPDATE (admin). */
    public PromoResponse update(Long id, PromoRequest req) {
        PromoCode promo = promos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promo code not found: " + id));
        String code = PromoCode.normalize(req.code());
        promos.findByCode(code).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new BusinessException("A code with that name already exists");
        });
        promo.update(code, parseType(req.type()), req.value(), req.minSpend(), req.validFrom(),
                req.validUntil(), req.maxUses(), req.description());
        return PromoResponse.from(promo);
    }

    /** DELETE (admin). Bookings that already used the code keep their discount. */
    public void delete(Long id) {
        PromoCode promo = promos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promo code not found: " + id));
        promos.delete(promo);
    }

    /** READ (customer): does this code apply to a basket of this value? Nothing is consumed. */
    @Transactional(readOnly = true)
    public PromoCheckResponse check(PromoCheckRequest req) {
        String code = PromoCode.normalize(req.code());
        PromoCode promo = promos.findByCode(code).orElse(null);
        if (promo == null) {
            return new PromoCheckResponse(code, false, BigDecimal.ZERO.setScale(2), req.total(),
                    "That promo code does not exist");
        }
        LocalDate today = LocalDate.now();
        if (!promo.isUsableFor(req.total(), today)) {
            return new PromoCheckResponse(code, false, BigDecimal.ZERO.setScale(2), req.total(),
                    promo.refusalReason(req.total(), today));
        }
        BigDecimal discount = promo.discountFor(req.total());
        return new PromoCheckResponse(code, true, discount, req.total().subtract(discount),
                "Code applied: " + promo.getDescription());
    }

    /**
     * Used by the booking flow: returns the code and its discount, or throws a message the customer can read.
     * The use is counted here, because this is the moment the booking keeps the discount.
     */
    public PromoQuote applyTo(Long promotion, String code, BigDecimal total) {
        String wanted = PromoCode.normalize(code);
        PromoCode promo = promos.findByCode(wanted)
                .orElseThrow(() -> new BusinessException("That promo code does not exist"));
        LocalDate today = LocalDate.now();
        if (!promo.isUsableFor(total, today)) {
            throw new BusinessException(promo.refusalReason(total, today));
        }
        BigDecimal discount = promo.discountFor(total);
        promo.recordUse();
        return new PromoQuote(promo.getCode(), discount, total.subtract(discount));
    }

    private static PromoType parseType(String raw) {
        try {
            return PromoType.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException("Promo type must be PERCENT or FLAT");
        }
    }
}
