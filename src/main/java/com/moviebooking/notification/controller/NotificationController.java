package com.moviebooking.notification.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.notification.dto.NotificationDto.NotificationInbox;
import com.moviebooking.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

/** Customer inbox (/api/customer/notifications): READ the list, UPDATE the read state. */
@RestController
@RequestMapping("/api/customer/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationInbox inbox(@SessionAttribute(SessionKeys.USER_ID) Long userId) {
        return notificationService.inbox(userId);
    }

    @PostMapping("/{id}/read")
    public NotificationInbox markRead(@SessionAttribute(SessionKeys.USER_ID) Long userId, @PathVariable Long id) {
        return notificationService.markRead(userId, id);
    }

    @PostMapping("/read-all")
    public NotificationInbox markAllRead(@SessionAttribute(SessionKeys.USER_ID) Long userId) {
        return notificationService.markAllRead(userId);
    }
}
