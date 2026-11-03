package com.moviebooking.user.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.user.dto.UserDto.CreateAdminRequest;
import com.moviebooking.user.dto.UserDto.UpdateAdminRequest;
import com.moviebooking.user.dto.UserDto.UserResponse;
import com.moviebooking.user.entity.Admin;
import com.moviebooking.user.repository.AdminRepository;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * CRUD for admin accounts.
 * Only SUPER_ADMIN accounts may create, change or delete admins (ABSTRACTION via Admin.canManageAdmins()).
 */
@Service
@Transactional
public class AdminService {

    private final AdminRepository admins;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminService(AdminRepository admins, UserRepository users, PasswordEncoder encoder) {
        this.admins = admins;
        this.users = users;
        this.encoder = encoder;
    }

    /** READ: all admins (SUPER_ADMIN only; STAFF cannot see the admin list). */
    @Transactional(readOnly = true)
    public List<UserResponse> listAdmins(Long actorId) {
        requireSuperAdmin(actorId);
        return admins.findAll(Sort.by("fullName")).stream().map(UserResponse::from).toList();
    }

    /**
     * CREATE: add an admin.
     * actorId == null means a trusted server-side call (used by the seed-file importer).
     */
    public UserResponse createAdmin(Long actorId, CreateAdminRequest req) {
        requireSuperAdmin(actorId);
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("An account with this email already exists");
        }
        Admin admin = new Admin(req.fullName().trim(), email, encoder.encode(req.password()), null,
                normalizeLevel(req.permissionLevel()));
        return UserResponse.from(admins.save(admin));
    }

    /** CREATE (seed-file import): stores a password hash that was already produced by BCrypt. */
    public UserResponse importAdmin(String fullName, String email, String passwordHash, String level) {
        String normalised = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalised)) {
            throw new BusinessException("An account with this email already exists");
        }
        Admin admin = new Admin(fullName.trim(), normalised, passwordHash, null, normalizeLevel(level));
        return UserResponse.from(admins.save(admin));
    }

    /** UPDATE: change an admin's permission level. */
    public UserResponse updatePermission(Long actorId, Long id, UpdateAdminRequest req) {
        requireSuperAdmin(actorId);
        Admin admin = findAdmin(id);
        admin.setPermissionLevel(normalizeLevel(req.permissionLevel()));
        return UserResponse.from(admin);
    }

    /** DELETE: remove an admin (you cannot delete your own account). */
    public void deleteAdmin(Long actorId, Long id) {
        requireSuperAdmin(actorId);
        if (Objects.equals(actorId, id)) {
            throw new BusinessException("You cannot delete your own admin account");
        }
        admins.delete(findAdmin(id));
    }

    private void requireSuperAdmin(Long actorId) {
        if (actorId == null) {
            return;
        }
        Admin actor = admins.findById(actorId)
                .orElseThrow(() -> new BusinessException("Admin session is no longer valid"));
        if (!actor.canManageAdmins()) {
            throw new BusinessException("Only SUPER_ADMIN accounts can manage other admins");
        }
    }

    private Admin findAdmin(Long id) {
        return admins.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + id));
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return Admin.STAFF;
        }
        String value = level.trim().toUpperCase().replace(' ', '_');
        if (!Admin.SUPER_ADMIN.equals(value) && !Admin.STAFF.equals(value) && !Admin.BOX_OFFICE.equals(value)) {
            throw new BusinessException("Permission level must be SUPER_ADMIN, STAFF or BOX_OFFICE");
        }
        return value;
    }
}
