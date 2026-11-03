package com.moviebooking.user.service;

import com.moviebooking.user.entity.User;
import com.moviebooking.user.service.auth.AdminAuthStrategy;
import com.moviebooking.user.service.auth.AuthStrategy;
import com.moviebooking.user.service.auth.CustomerAuthStrategy;
import org.springframework.stereotype.Service;

/**
 * Coordinates the two separate login methods.
 * POLYMORPHISM: login(strategy, ...) runs the same flow for any AuthStrategy implementation.
 */
@Service
public class AuthService {

    private final CustomerAuthStrategy customerStrategy;
    private final AdminAuthStrategy adminStrategy;

    public AuthService(CustomerAuthStrategy customerStrategy, AdminAuthStrategy adminStrategy) {
        this.customerStrategy = customerStrategy;
        this.adminStrategy = adminStrategy;
    }

    public User login(AuthStrategy strategy, String email, String password) {
        return strategy.authenticate(email, password);
    }

    public User loginCustomer(String email, String password) {
        return login(customerStrategy, email, password);
    }

    public User loginAdmin(String email, String password) {
        return login(adminStrategy, email, password);
    }
}
