package com.moviebooking.booking.controller;

import com.moviebooking.booking.dto.BookingDto.BookingRequest;
import com.moviebooking.booking.dto.BookingDto.BookingResponse;
import com.moviebooking.booking.dto.BookingDto.BookingUpdateRequest;
import com.moviebooking.booking.dto.BookingDto.CancelPreview;
import com.moviebooking.booking.dto.BookingDto.CancelSeatsRequest;
import com.moviebooking.booking.dto.BookingDto.CancellationResult;
import com.moviebooking.booking.dto.BookingDto.RescheduleRequest;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.config.SessionKeys;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Customer booking CRUD (/api/customer/bookings) and admin booking views (/api/admin/bookings). */
@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/customer/bookings")
    public ResponseEntity<BookingResponse> create(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                                  @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(userId, request));
    }

    @GetMapping("/api/customer/bookings")
    public List<BookingResponse> listMine(@SessionAttribute(SessionKeys.USER_ID) Long userId) {
        return bookingService.listMine(userId);
    }

    @GetMapping("/api/customer/bookings/{id}")
    public BookingResponse getMine(@SessionAttribute(SessionKeys.USER_ID) Long userId, @PathVariable Long id) {
        return bookingService.getMine(userId, id);
    }

    @PutMapping("/api/customer/bookings/{id}")
    public BookingResponse updateSeats(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.updateSeats(userId, id, request);
    }

    /** What cancelling would refund right now. Read only. */
    @GetMapping("/api/customer/bookings/{id}/cancel-preview")
    public CancelPreview cancelPreview(@SessionAttribute(SessionKeys.USER_ID) Long userId, @PathVariable Long id) {
        return bookingService.cancelPreview(userId, id);
    }

    /** Moves a confirmed booking to another showtime of the same film. */
    @PostMapping("/api/customer/bookings/{id}/reschedule")
    public BookingResponse reschedule(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody RescheduleRequest request) {
        return bookingService.reschedule(userId, id, request);
    }

    /** UPDATE (partial cancel): give some of the seats back and keep the rest. */
    @PostMapping("/api/customer/bookings/{id}/cancel-seats")
    public CancellationResult cancelSeats(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CancelSeatsRequest request) {
        return bookingService.cancelSeats(userId, id, request);
    }

    @DeleteMapping("/api/customer/bookings/{id}")
    public CancellationResult cancel(@SessionAttribute(SessionKeys.USER_ID) Long userId, @PathVariable Long id) {
        return bookingService.cancel(userId, id);
    }

    @GetMapping("/api/admin/bookings")
    public List<BookingResponse> listAll() {
        return bookingService.listAll();
    }

    /** READ (admin): one page of bookings, for the paged admin table. */
    @GetMapping("/api/admin/bookings/page")
    public Page<BookingResponse> page(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return bookingService.page(page, size);
    }

    @DeleteMapping("/api/admin/bookings/{id}")
    public Map<String, String> adminDelete(@PathVariable Long id) {
        bookingService.adminDelete(id);
        return Map.of("message", "Cancelled booking removed");
    }
}
