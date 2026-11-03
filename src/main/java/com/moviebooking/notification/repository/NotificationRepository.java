package com.moviebooking.notification.repository;

import com.moviebooking.notification.entity.Notification;
import com.moviebooking.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Data access for customer notifications. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByCustomerIdAndReadAtIsNull(Long customerId);

    boolean existsByBookingIdAndType(Long bookingId, NotificationType type);

    void deleteByCustomerId(Long customerId);
}
