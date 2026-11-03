package com.moviebooking.user.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.user.dto.UserDto.CustomerUpdateRequest;
import com.moviebooking.user.dto.UserDto.UserResponse;
import com.moviebooking.user.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Customer manages their own profile (READ / UPDATE / DELETE own account). */
@RestController
@RequestMapping("/api/customer/profile")
public class CustomerProfileController {

    private final CustomerService customerService;

    public CustomerProfileController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public UserResponse get(@SessionAttribute(SessionKeys.USER_ID) Long userId) {
        return customerService.getById(userId);
    }

    @PutMapping
    public UserResponse update(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                               @Valid @RequestBody CustomerUpdateRequest request) {
        return customerService.updateProfile(userId, request);
    }

    @DeleteMapping
    public Map<String, String> delete(@SessionAttribute(SessionKeys.USER_ID) Long userId,
                                      HttpServletRequest http) {
        customerService.delete(userId);
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("message", "Your account has been deleted");
    }
}
