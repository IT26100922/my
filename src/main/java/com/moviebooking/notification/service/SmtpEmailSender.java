package com.moviebooking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends customer emails (booking confirmations, show reminders, hold expiry) through an SMTP server.
 * Active only when app.email.mode=smtp. Connection details come from spring.mail.* (see application.properties).
 * A failed send is logged and never thrown, so a mail outage cannot undo a booking.
 */
@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${app.email.from:CineMax <bookings@cinemax.lk>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
        }
    }

    @Override
    public void send(String to, String subject, String body, EmailAttachment attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.data()), attachment.contentType());
            mailSender.send(message);
            log.info("Email with attachment sent to {}: {}", to, subject);
        } catch (MessagingException | MailException e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
        }
    }
}
