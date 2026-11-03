package com.moviebooking.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * An administrator who manages movies, theatres, shows, bookings and reports.
 * INHERITANCE: extends User.
 * ABSTRACTION: callers ask canManageAdmins() instead of comparing permission strings.
 */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String STAFF = "STAFF";
    /** Door staff: may scan tickets and look at bookings, nothing else. */
    public static final String BOX_OFFICE = "BOX_OFFICE";

    @Column(name = "permission_level", length = 30)
    private String permissionLevel;

    protected Admin() {
        // required by JPA
    }

    public Admin(String fullName, String email, String passwordHash, String phone, String permissionLevel) {
        super(fullName, email, passwordHash, phone);
        setPermissionLevel(permissionLevel);
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    /** INFORMATION HIDING / VALIDATION: only the three known levels are accepted. */
    public void setPermissionLevel(String permissionLevel) {
        this.permissionLevel = normalizeLevel(permissionLevel);
    }

    /** Accepts the level in any letter case and with spaces or dashes, and returns the stored form. */
    public static String normalizeLevel(String permissionLevel) {
        String level = permissionLevel == null ? STAFF : permissionLevel.trim().toUpperCase().replace(' ', '_');
        if (!SUPER_ADMIN.equals(level) && !STAFF.equals(level) && !BOX_OFFICE.equals(level)) {
            throw new IllegalArgumentException("Permission level must be SUPER_ADMIN, STAFF or BOX_OFFICE");
        }
        return level;
    }

    public boolean canManageAdmins() {
        return SUPER_ADMIN.equals(permissionLevel);
    }

    /** Door staff may only sell seats and scan tickets. */
    public boolean isBoxOffice() {
        return BOX_OFFICE.equals(permissionLevel);
    }
}
