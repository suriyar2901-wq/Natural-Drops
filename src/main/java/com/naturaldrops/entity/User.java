package com.naturaldrops.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(name = "full_name", length = 100)
    private String fullName;
    
    @JsonIgnore // Never serialize password in JSON responses - security best practice
    @Column(nullable = false)
    private String password;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20) // Temporarily nullable for migration, will be set to NOT NULL after migration
    private UserStatus status = UserStatus.PENDING;
    
    @Column(name = "is_active", nullable = true) // Temporarily nullable for migration, will be set to NOT NULL after migration
    private Boolean isActive = false; // Default to false - admin must activate accounts
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    // New fields
    @Column(length = 10)
    private String gender; // MALE, FEMALE, OTHER, or null
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;
    
    @Column(name = "profile_photo", columnDefinition = "TEXT")
    private String profilePhoto; // URL or base64
    
    // Structured address fields (replacing single address field)
    @Column(name = "house_door_no", length = 50)
    private String houseDoorNo;
    
    @Column(name = "street_area", length = 200)
    private String streetArea;
    
    @Column(length = 100)
    private String city;
    
    @Column(length = 100)
    private String district;
    
    @Column(length = 100)
    private String state;
    
    @Column(length = 10)
    private String pincode;
    
    @Column(length = 200)
    private String landmark;
    
    // Keep old address field for backward compatibility (will be deprecated)
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 50)
    private String createdBy;
    
    @Column(name = "device_token", columnDefinition = "TEXT")
    private String deviceToken;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum UserRole {
        buyer, seller, admin
    }
    
    public enum UserStatus {
        PENDING,    // Waiting for admin approval
        APPROVED,   // Approved by admin, can access app
        REJECTED,   // Registration rejected
        BLOCKED     // Account blocked/restricted
    }
}

