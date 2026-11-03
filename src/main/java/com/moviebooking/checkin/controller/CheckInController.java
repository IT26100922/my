package com.moviebooking.checkin.controller;

import com.moviebooking.checkin.dto.CheckInDto.CheckInSummary;
import com.moviebooking.checkin.dto.CheckInDto.ScanResult;
import com.moviebooking.checkin.service.CheckInService;
import com.moviebooking.config.SessionKeys;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Door check-in: one endpoint to scan a ticket, one to read today's attendance. */
@RestController
public class CheckInController {

    private final CheckInService checkIn;

    public CheckInController(CheckInService checkIn) {
        this.checkIn = checkIn;
    }

    @PostMapping("/api/admin/tickets/validate")
    public ScanResult validate(@RequestBody Map<String, String> body,
                               @SessionAttribute(value = SessionKeys.USER_NAME, required = false) String staff) {
        return checkIn.scan(body.get("code"), staff);
    }

    @GetMapping("/api/admin/tickets/today")
    public CheckInSummary today() {
        return checkIn.today();
    }
}
