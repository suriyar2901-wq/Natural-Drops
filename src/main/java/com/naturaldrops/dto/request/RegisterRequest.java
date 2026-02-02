package com.naturaldrops.dto.request;

import com.naturaldrops.entity.User;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    @NotNull(message = "Role is required")
    private User.UserRole role;
    
    private String email;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be 10 digits")
    private String phone;
    
    // New optional fields
    private String gender; // MALE, FEMALE, OTHER
    private LocalDate dateOfBirth;
    
    @Pattern(regexp = "^[0-9]{10}$", message = "Alternate phone number must be 10 digits")
    private String alternatePhone;
    
    private String profilePhoto; // URL or base64
    
    // Structured address fields (mandatory for registration)
    @NotBlank(message = "House/Door number is required")
    @Size(max = 50, message = "House/Door number must not exceed 50 characters")
    private String houseDoorNo;
    
    @NotBlank(message = "Street/Area is required")
    @Size(max = 200, message = "Street/Area must not exceed 200 characters")
    private String streetArea;
    
    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    @NotBlank(message = "District is required")
    @Size(max = 100, message = "District must not exceed 100 characters")
    private String district;
    
    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;
    
    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be 6 digits")
    private String pincode;
    
    @Size(max = 200, message = "Landmark must not exceed 200 characters")
    private String landmark; // Optional
    
    // Keep old address field for backward compatibility
    private String address;
}

