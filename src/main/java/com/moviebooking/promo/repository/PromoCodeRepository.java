package com.moviebooking.promo.repository;

import com.moviebooking.promo.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data access for promo codes. */
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    List<PromoCode> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);
}
