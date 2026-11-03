package com.moviebooking.ticket.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.ticket.service.TicketService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/** Ticket downloads for a customer's own paid bookings (/api/customer/bookings/{id}/...). */
@RestController
@RequestMapping("/api/customer/bookings/{id}")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/ticket.pdf")
    public ResponseEntity<byte[]> ticketPdf(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                            @PathVariable Long id) {
        byte[] pdf = tickets.ticketPdf(userId, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(tickets.fileName(userId, id)).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrPng(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                        @PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(tickets.qrPng(userId, id));
    }
}
