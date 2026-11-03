package com.moviebooking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simulated email: appends each message to <outbox-dir>/outbox.txt and writes it to the log.
 * No mail server is contacted. This is the default (app.email.mode=outbox). Folder set by app.email.outbox-dir.
 * Use SmtpEmailSender (app.email.mode=smtp) to send real email.
 */
@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "outbox", matchIfMissing = true)
public class OutboxEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxEmailSender.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path outboxFile;

    public OutboxEmailSender(@Value("${app.email.outbox-dir:data/outbox}") String outboxDir) {
        this.outboxFile = Path.of(outboxDir).resolve("outbox.txt");
    }

    @Override
    public synchronized void send(String to, String subject, String body) {
        String entry = "----- " + LocalDateTime.now().format(STAMP) + " -----\n"
                + "To: " + to + "\n"
                + "Subject: " + subject + "\n\n"
                + body + "\n\n";
        log.info("Email to {}: {}", to, subject);
        try {
            Files.createDirectories(outboxFile.getParent());
            Files.writeString(outboxFile, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not write email to {}: {}", outboxFile, e.getMessage());
        }
    }
}
