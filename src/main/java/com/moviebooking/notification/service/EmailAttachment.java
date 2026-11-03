package com.moviebooking.notification.service;

/** A file sent with an email, such as the PDF ticket. */
public record EmailAttachment(String fileName, String contentType, byte[] data) {
}
