package com.moviebooking.notification.dto;

import com.moviebooking.notification.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

/** DTOs for customer notifications. */
public final class NotificationDto {

    private NotificationDto() {
    }

    public record NotificationResponse(
            Long id,
            String type,
            String message,
            Long bookingId,
            String bookingReference,
            LocalDateTime createdAt,
            boolean read) {

        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType().name(), n.getMessage(), n.getBookingId(),
                    n.getBookingReference(), n.getCreatedAt(), n.isRead());
        }
    }

    /** The inbox: unread count plus the newest notifications. */
    public record NotificationInbox(long unreadCount, List<NotificationResponse> items) {
    }
}
