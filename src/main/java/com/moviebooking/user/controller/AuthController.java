package com.moviebooking.user.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.user.dto.AuthDto.LoginRequest;
import com.moviebooking.user.dto.AuthDto.RegisterCustomerRequest;
import com.moviebooking.user.dto.AuthDto.SessionResponse;
import com.moviebooking.user.dto.UserDto.UserResponse;
import com.moviebooking.user.entity.Admin;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.entity.User;
import com.moviebooking.user.service.AuthService;
import com.moviebooking.user.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints.
 * Two SEPARATE login methods: /customer/login (customer portal) and /admin/login (admin portal).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CustomerService customerService;

    public AuthController(AuthService authService, CustomerService customerService) {
        this.authService = authService;
        this.customerService = customerService;
    }

    @PostMapping("/customer/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.register(request));
    }

    @PostMapping("/customer/login")
    public SessionResponse customerLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        User user = authService.loginCustomer(request.email(), request.password());
        startSession(http, user);
        return toSession(user);
    }

    @PostMapping("/admin/login")
    public SessionResponse adminLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        User user = authService.loginAdmin(request.email(), request.password());
        startSession(http, user);
        return toSession(user);
    }

    /** Ends the session on the server and tells the browser to drop the session cookie. */
    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest http, HttpServletResponse response) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ResponseCookie expired = ResponseCookie.from("JSESSIONID", "")
                .path("/").maxAge(0).httpOnly(true).sameSite("Lax").build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return Map.of("message", "Logged out");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session == null || session.getAttribute(SessionKeys.USER_ID) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not logged in"));
        }
        return ResponseEntity.ok(new SessionResponse(
                (Long) session.getAttribute(SessionKeys.USER_ID),
                (String) session.getAttribute(SessionKeys.USER_NAME),
                (String) session.getAttribute(SessionKeys.EMAIL),
                (String) session.getAttribute(SessionKeys.ROLE),
                null,
                (String) session.getAttribute(SessionKeys.LEVEL)));
    }

    /** Creates a fresh session on login (prevents session-fixation) and stores who is logged in. */
    private void startSession(HttpServletRequest http, User user) {
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.ROLE, user.getRole().name());
        session.setAttribute(SessionKeys.USER_NAME, user.getFullName());
        session.setAttribute(SessionKeys.EMAIL, user.getEmail());
        session.setAttribute(SessionKeys.LEVEL, user instanceof Admin a ? a.getPermissionLevel() : null);
    }

    private SessionResponse toSession(User user) {
        Integer points = user instanceof Customer c ? c.getLoyaltyPoints() : null;
        String level = user instanceof Admin a ? a.getPermissionLevel() : null;
        return new SessionResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().name(), points, level);
    }
}
