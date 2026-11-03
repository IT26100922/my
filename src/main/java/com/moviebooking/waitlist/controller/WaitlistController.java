package com.moviebooking.waitlist.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.waitlist.dto.WaitlistDto.WaitlistResponse;
import com.moviebooking.waitlist.service.WaitlistService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** "Notify me" for a sold-out showtime. */
@RestController
@RequestMapping("/api/customer/waitlist")
public class WaitlistController {

    private final WaitlistService waitlist;

    public WaitlistController(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    @GetMapping
    public List<WaitlistResponse> mine(@SessionAttribute(SessionKeys.USER_ID) Long customerId) {
        return waitlist.mine(customerId);
    }

    @PostMapping("/{showId}")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistResponse join(@SessionAttribute(SessionKeys.USER_ID) Long customerId,
                                 @PathVariable Long showId) {
        return waitlist.join(customerId, showId);
    }

    @DeleteMapping("/{showId}")
    public void leave(@SessionAttribute(SessionKeys.USER_ID) Long customerId, @PathVariable Long showId) {
        waitlist.leave(customerId, showId);
    }
}
