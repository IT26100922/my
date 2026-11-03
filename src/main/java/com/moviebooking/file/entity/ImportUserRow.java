package com.moviebooking.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the MySQL import table {@code import_users}.
 * <p>
 * The staging area for the "Import users" button. {@code passwordHash} holds a BCrypt hash
 * (the same shape the app stores), so no readable password ever passes through this table.
 */
@Entity
@Table(name = "import_users")
public class ImportUserRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ADMIN or CUSTOMER. */
    @Column(nullable = false, length = 20)
    private String accountType;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    /** SUPER_ADMIN or STAFF; empty for customers. */
    @Column(length = 30)
    private String permissionLevel;

    protected ImportUserRow() {
    }

    public Long getId() {
        return id;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    /** Used when the live table is copied back into the staging table. */
    public void setFrom(String accountType, String fullName, String email, String passwordHash,
                        String phone, String permissionLevel) {
        this.accountType = accountType;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.permissionLevel = permissionLevel;
    }
}
