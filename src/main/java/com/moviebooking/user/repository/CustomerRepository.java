package com.moviebooking.user.repository;

import com.moviebooking.user.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository that only returns CUSTOMER rows. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByFullNameAsc(
            String name, String email);

    /** Paged version used by the admin table. */
    Page<Customer> findAllByOrderByFullNameAsc(Pageable pageable);
}
