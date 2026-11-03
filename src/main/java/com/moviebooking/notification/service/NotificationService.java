package com.moviebooking.notification.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.notification.dto.NotificationDto.NotificationInbox;
import com.moviebooking.notification.dto.NotificationDto.NotificationResponse;
import com.moviebooking.notification.entity.Notification;
import com.moviebooking.notification.entity.NotificationType;
import com.moviebooking.notification.repository.NotificationRepository;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer notifications and show reminders.
 * CREATE: notifyBookingConfirmed() / notifyBookingCancelled() / runReminderSweep()
 * READ: inbox()   UPDATE: markRead() / markAllRead()   DELETE: removed with the customer account
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final NotificationRepository notifications;
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final EmailSender emails;

    public NotificationService(NotificationRepository notifications, BookingRepository bookings,
                               CustomerRepository customers, EmailSender emails) {
        this.notifications = notifications;
        this.bookings = bookings;
        this.customers = customers;
        this.emails = emails;
    }

    /** READ: newest notifications and the unread count for the bell icon. */
    @Transactional(readOnly = true)
    public NotificationInbox inbox(Long customerId) {
        List<NotificationResponse> items = notifications
                .findTop30ByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(NotificationResponse::from).collect(Collectors.toList());
        return new NotificationInbox(notifications.countByCustomerIdAndReadAtIsNull(customerId), items);
    }

    /** UPDATE: mark one of the customer's notifications as read. */
    public NotificationInbox markRead(Long customerId, Long id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        n.markRead();
        return inbox(customerId);
    }

    /** UPDATE: mark every notification of the customer as read. */
    public NotificationInbox markAllRead(Long customerId) {
        notifications.findTop30ByCustomerIdOrderByCreatedAtDesc(customerId).forEach(Notification::markRead);
        return inbox(customerId);
    }

    /** CREATE: called after a payment confirms a booking. */
    public void notifyBookingConfirmed(Booking booking) {
        add(booking, NotificationType.BOOKING_CONFIRMED,
                "Booking " + booking.getReference() + " confirmed: " + describe(booking) + ".");
    }

    /** CREATE: confirmation with the PDF ticket attached (when the ticket could be produced). */
    public void notifyBookingConfirmed(Booking booking, EmailAttachment ticket) {
        String message = "Booking " + booking.getReference() + " confirmed: " + describe(booking) + ".";
        notifications.save(new Notification(booking.getCustomer(), booking.getId(), booking.getReference(),
                NotificationType.BOOKING_CONFIRMED, message));
        String subject = subjectFor(NotificationType.BOOKING_CONFIRMED) + " " + booking.getReference();
        String body = message + emailFooter(booking);
        if (ticket == null) {
            emails.send(booking.getCustomer().getEmail(), subject, body);
        } else {
            emails.send(booking.getCustomer().getEmail(), subject, body, ticket);
        }
    }

    /** CREATE: called after a confirmed booking moves to another showtime. */
    public void notifyBookingMoved(Booking booking, Showtime from) {
        String message = "Booking " + booking.getReference() + " moved from " + from.getStartTime().format(WHEN)
                + " to " + booking.getShow().getStartTime().format(WHEN) + " at "
                + booking.getShow().getScreen().getTheatre().getName() + " / " + booking.getShow().getScreen().getName()
                + ", seats " + String.join(", ", booking.getSeatCodes().stream().sorted().toList()) + ".";
        add(booking, NotificationType.BOOKING_MOVED, message);
    }

    /** CREATE: called after a booking is cancelled by the customer or the admin. */
    public void notifyBookingCancelled(Booking booking, BigDecimal refund) {
        String refundText = refund.signum() > 0 ? " Refund: LKR " + refund.setScale(2) + "." : "";
        add(booking, NotificationType.BOOKING_CANCELLED,
                "Booking " + booking.getReference() + " for " + booking.getShow().getMovie().getTitle()
                        + " was cancelled." + refundText);
    }

    /** CREATE: called when an unpaid seat hold runs out and the seats are released. */
    public void notifyHoldExpired(Customer customer, String reference, String movieTitle) {
        String message = "Your seat hold for " + movieTitle + " (" + reference + ") expired. Reserve the seats again to book.";
        notifications.save(new Notification(customer, null, reference, NotificationType.HOLD_EXPIRED, message));
        emails.send(customer.getEmail(), "Seat hold expired: " + reference, message);
    }

    /** CREATE: called when seats are freed for a show the customer is waiting for. */
    public void notifyWaitlist(Customer customer, String movieTitle, LocalDateTime showTime, int priorityMinutes) {
        String message = "A seat just freed up for " + movieTitle + " on " + showTime.format(WHEN)
                + ". You are on the waitlist, so you have " + priorityMinutes
                + " minutes to book it before it is offered to everyone.";
        notifications.save(new Notification(customer, null, null, NotificationType.WAITLIST_SEAT_FREE, message));
        emails.send(customer.getEmail(), "A seat freed up: " + movieTitle, message);
    }

    /** CREATE: called when a customer gives part of a booking back. */
    public void notifySeatsReleased(Booking booking, java.util.Collection<String> seats, BigDecimal refund) {
        String refundText = refund.signum() > 0 ? " LKR " + refund.setScale(2) + " went back to your wallet." : "";
        add(booking, NotificationType.SEATS_RELEASED, "Seat(s) " + String.join(", ", seats)
                + " released from booking " + booking.getReference() + "." + refundText);
    }

    /**
     * Runs every minute (see app.reminders.interval-ms). Creates one reminder per booking and window:
     * - 2 hours before the show: bookings starting in the next 2 hours;
     * - 24 hours before the show: bookings starting between 2 and 24 hours from now.
     * Each booking gets at most one reminder per window, so a late booking gets only the closer reminder.
     */
    @Scheduled(fixedDelayString = "${app.reminders.interval-ms:60000}", initialDelayString = "${app.reminders.initial-delay-ms:15000}")
    public void runReminderSweep() {
        LocalDateTime now = LocalDateTime.now();
        int created = sendWindow(now, now.plusHours(2), NotificationType.REMINDER_2H)
                + sendWindow(now.plusHours(2), now.plusHours(24), NotificationType.REMINDER_24H);
        if (created > 0) {
            log.info("Created {} show reminder(s)", created);
        }
    }

    private int sendWindow(LocalDateTime from, LocalDateTime to, NotificationType type) {
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        for (Booking b : bookings.findStartingBetween(BookingStatus.CONFIRMED, from, to)) {
            if (notifications.existsByBookingIdAndType(b.getId(), type)) {
                continue;
            }
            add(b, type, "Reminder: " + b.getShow().getMovie().getTitle() + " starts in " + timeUntil(now, b.getShow().getStartTime()) + " at "
                    + b.getShow().getScreen().getTheatre().getName() + " (" + b.getShow().getScreen().getName()
                    + "). Seats " + String.join(", ", b.getSeatCodes().stream().sorted().toList()) + ". Ref "
                    + b.getReference() + ".");
            created++;
        }
        return created;
    }

    /** Removes everything a deleted customer had (their bookings are checked separately). */
    public void deleteForCustomer(Long customerId) {
        notifications.deleteByCustomerId(customerId);
    }

    private void add(Booking booking, NotificationType type, String message) {
        notifications.save(new Notification(booking.getCustomer(), booking.getId(), booking.getReference(),
                type, message));
        String body = (type == NotificationType.BOOKING_CANCELLED || type == NotificationType.HOLD_EXPIRED)
                ? message : message + emailFooter(booking);
        emails.send(booking.getCustomer().getEmail(), subjectFor(type) + " " + booking.getReference(), body);
    }

    /** Calendar link and ticket hint added to show-related emails. */
    private String emailFooter(Booking b) {
        return "\n\nAdd this show to your calendar: " + calendarLink(b)
                + "\nYour ticket and booking details are in My bookings on CineMax.";
    }

    /** Google Calendar "add event" link. Times are sent as Asia/Colombo local time. */
    static String calendarLink(Booking b) {
        LocalDateTime start = b.getShow().getStartTime();
        Integer minutes = b.getShow().getMovie().getDurationMinutes();
        LocalDateTime end = start.plusMinutes(minutes == null ? 120 : minutes);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + enc(b.getShow().getMovie().getTitle() + " at CineMax")
                + "&dates=" + start.format(fmt) + "/" + end.format(fmt) + "&ctz=Asia%2FColombo"
                + "&location=" + enc(b.getShow().getScreen().getTheatre().getName())
                + "&details=" + enc("Booking " + b.getReference() + ", seats "
                + String.join(", ", b.getSeatCodes().stream().sorted().toList()));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String subjectFor(NotificationType type) {
        return switch (type) {
            case BOOKING_CONFIRMED -> "Booking confirmed";
            case BOOKING_CANCELLED -> "Booking cancelled";
            case BOOKING_MOVED -> "Booking moved";
            case REMINDER_24H, REMINDER_2H -> "Show reminder";
            case HOLD_EXPIRED -> "Seat hold expired";
            case WAITLIST_SEAT_FREE -> "A seat freed up";
            case SEATS_RELEASED -> "Seats released";
        };
    }

    /** Human wording such as "1 h 25 min" or "23 h 50 min" for the reminder text. */
    static String timeUntil(LocalDateTime now, LocalDateTime start) {
        long minutes = Math.max(0, java.time.Duration.between(now, start).toMinutes());
        return minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min";
    }

    private String describe(Booking b) {
        return b.getShow().getMovie().getTitle() + ", " + b.getShow().getStartTime().format(WHEN)
                + ", " + b.getShow().getScreen().getTheatre().getName() + " / " + b.getShow().getScreen().getName()
                + ", seats " + String.join(", ", b.getSeatCodes().stream().sorted().toList());
    }
}
