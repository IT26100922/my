package com.moviebooking.user.controller;

import com.moviebooking.config.SessionKeys;
import com.moviebooking.user.dto.UserDto.CreateAdminRequest;
import com.moviebooking.user.dto.UserDto.UpdateAdminRequest;
import com.moviebooking.user.dto.UserDto.UserResponse;
import com.moviebooking.user.service.AdminService;
import com.moviebooking.user.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-side user management: customers (READ/DELETE) and admins (full CRUD). */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final CustomerService customerService;
    private final AdminService adminService;

    public AdminUserController(CustomerService customerService, AdminService adminService) {
        this.customerService = customerService;
        this.adminService = adminService;
    }

    // ----- customers -----
    @GetMapping("/customers")
    public List<UserResponse> searchCustomers(@RequestParam(defaultValue = "") String q) {
        return customerService.search(q);
    }

    /** READ (admin): one page of customers, for the paged admin table. */
    @GetMapping("/customers/page")
    public org.springframework.data.domain.Page<UserResponse> pageCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerService.page(page, size);
    }

    @GetMapping("/customers/{id}")
    public UserResponse getCustomer(@PathVariable Long id) {
        return customerService.getById(id);
    }

    @DeleteMapping("/customers/{id}")
    public Map<String, String> deleteCustomer(@PathVariable Long id) {
        customerService.delete(id);
        return Map.of("message", "Customer deleted");
    }

    // ----- admins -----
    @GetMapping("/admins")
    public List<UserResponse> listAdmins(@SessionAttribute(SessionKeys.USER_ID) Long actorId) {
        return adminService.listAdmins(actorId);
    }

    @PostMapping("/admins")
    public ResponseEntity<UserResponse> createAdmin(
            @SessionAttribute(SessionKeys.USER_ID) Long actorId,
            @Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createAdmin(actorId, request));
    }

    @PutMapping("/admins/{id}")
    public UserResponse updateAdmin(@SessionAttribute(SessionKeys.USER_ID) Long actorId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody UpdateAdminRequest request) {
        return adminService.updatePermission(actorId, id, request);
    }

    @DeleteMapping("/admins/{id}")
    public Map<String, String> deleteAdmin(@SessionAttribute(SessionKeys.USER_ID) Long actorId,
                                           @PathVariable Long id) {
        adminService.deleteAdmin(actorId, id);
        return Map.of("message", "Admin deleted");
    }
}
