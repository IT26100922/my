package com.moviebooking.user.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Abstract base class for every person who can log in.
 * <p>
 * INHERITANCE: {@link Customer} and {@link Admin} extend this class.
 * They are stored in one table ("users") and told apart by the user_type column.
 * ENCAPSULATION: all fields are private; they are read/changed only through methods.
 * INFORMATION HIDING: the email cannot be changed after registration (no setter),
 * and the password is stored only as a BCrypt hash.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected User() {
        // required by JPA
    }

    protected User(String fullName, String email, String passwordHash, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.createdAt = LocalDateTime.now();
    }

    /** POLYMORPHISM: each subclass returns its own role. */
    public abstract UserRole getRole();

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
