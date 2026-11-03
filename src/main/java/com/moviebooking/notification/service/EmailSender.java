package com.moviebooking.notification.service;

/**
 * ABSTRACTION for sending customer emails. Services depend on this interface, so a real SMTP sender
 * can replace the outbox sender later without changing the notification code.
 */
public interface EmailSender {

    /** Sends (or records) one email. Must not throw: a failed email must not undo a booking. */
    void send(String to, String subject, String body);

    /**
     * Sends one email with a file attached. The default keeps working for senders that cannot attach files:
     * the file name is written into the body instead.
     */
    default void send(String to, String subject, String body, EmailAttachment attachment) {
        send(to, subject, body + "\n\nAttached: " + attachment.fileName());
    }
}
