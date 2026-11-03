package com.moviebooking.user.service;

import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.notification.service.NotificationService;
import com.moviebooking.user.dto.AuthDto.RegisterCustomerRequest;
import com.moviebooking.user.dto.UserDto.CustomerUpdateRequest;
import com.moviebooking.user.dto.UserDto.UserResponse;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for customers.
 * CREATE: register()   READ: search() / getById()   UPDATE: updateProfile()   DELETE: delete()
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final com.moviebooking.wallet.repository.WalletTransactionRepository walletLedger;
    private final com.moviebooking.waitlist.repository.WaitlistRepository waitlist;

    public CustomerService(CustomerRepository customers, UserRepository users,
                           BookingRepository bookings, PasswordEncoder encoder,
                           NotificationService notifications,
                           com.moviebooking.wallet.repository.WalletTransactionRepository walletLedger,
                           com.moviebooking.waitlist.repository.WaitlistRepository waitlist) {
        this.walletLedger = walletLedger;
        this.waitlist = waitlist;
        this.notifications = notifications;
        this.customers = customers;
        this.users = users;
        this.bookings = bookings;
        this.encoder = encoder;
    }

    /** CREATE: register a new customer account. */
    public UserResponse register(RegisterCustomerRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("An account with this email already exists");
        }
        Customer customer = new Customer(
                req.fullName().trim(), email, encoder.encode(req.password()), blankToNull(req.phone()));
        return UserResponse.from(customers.save(customer));
    }

    /** CREATE (seed-file import): stores a password hash that was already produced by BCrypt. */
    public UserResponse importCustomer(String fullName, String email, String passwordHash, String phone) {
        String normalised = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalised)) {
            throw new BusinessException("An account with this email already exists");
        }
        Customer customer = new Customer(fullName.trim(), normalised, passwordHash, blankToNull(phone));
        return UserResponse.from(customers.save(customer));
    }

    /** READ (admin): one page of customers, alphabetically. */
    @Transactional(readOnly = true)
    public Page<UserResponse> page(int page, int size) {
        return customers.findAllByOrderByFullNameAsc(
                        PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(UserResponse::from);
    }

    /** READ: search customers by name or email (empty keyword returns all). */
    @Transactional(readOnly = true)
    public List<UserResponse> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        return customers
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByFullNameAsc(k, k)
                .stream().map(UserResponse::from).toList();
    }

    /** READ: one customer. */
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findCustomer(id));
    }

    /** UPDATE: change name, phone and (optionally) password. Email is fixed after registration. */
    public UserResponse updateProfile(Long id, CustomerUpdateRequest req) {
        Customer customer = findCustomer(id);
        customer.setFullName(req.fullName().trim());
        customer.setPhone(blankToNull(req.phone()));
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            customer.setPasswordHash(encoder.encode(req.newPassword()));
        }
        return UserResponse.from(customer);
    }

    /** DELETE: remove an account. Blocked when the customer has booking history (keeps records consistent). */
    public void delete(Long id) {
        Customer customer = findCustomer(id);
        if (bookings.existsByCustomerId(id)) {
            throw new BusinessException("This customer has booking history and cannot be deleted");
        }
        // The wallet ledger and the waitlist point at the customer with a foreign key, and they are part
        // of the account itself, so they are removed with it. Bookings are different: they are the
        // cinema's records and that is why the check above refuses to delete an account that has any.
        walletLedger.deleteByCustomerId(id);
        waitlist.deleteByCustomerId(id);
        notifications.deleteForCustomer(id);
        customers.delete(customer);
    }

    private Customer findCustomer(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
