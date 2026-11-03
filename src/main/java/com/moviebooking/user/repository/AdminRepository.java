package com.moviebooking.user.repository;

import com.moviebooking.user.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository that only returns ADMIN rows. */
public interface AdminRepository extends JpaRepository<Admin, Long> {
}
