package com.naturaldrops.service;

import com.naturaldrops.entity.User;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
    
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }
    
    public List<User> getUsersByRole(User.UserRole role) {
        return userRepository.findByRole(role);
    }
    
    @Transactional
    public User createUser(User user, String createdBy) {
        // Check if username exists
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setCreatedBy(createdBy);
        
        return userRepository.save(user);
    }
    
    @Transactional
    public User updateUser(Long id, User userDetails) {
        User user = getUserById(id);
        
        // Only update username if provided and different
        if (userDetails.getUsername() != null && !userDetails.getUsername().isEmpty()) {
            // Check username uniqueness if changed
            if (!user.getUsername().equals(userDetails.getUsername()) && 
                userRepository.existsByUsername(userDetails.getUsername())) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUsername(userDetails.getUsername());
        }
        
        // Only update fullName if provided (can be empty string to clear)
        if (userDetails.getFullName() != null) {
            user.setFullName(userDetails.getFullName().isEmpty() ? null : userDetails.getFullName());
        }
        
        // Only update password if provided and different (not already encoded)
        if (userDetails.getPassword() != null && 
            !userDetails.getPassword().isEmpty() && 
            !userDetails.getPassword().equals(user.getPassword()) &&
            !userDetails.getPassword().startsWith("$2a$")) { // Don't re-encode already encoded passwords
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        
        // Only update role if provided
        if (userDetails.getRole() != null) {
            user.setRole(userDetails.getRole());
        }
        
        // Only update email if provided
        if (userDetails.getEmail() != null) {
            user.setEmail(userDetails.getEmail());
        }
        
        // Only update phone if provided (can be empty string to clear)
        if (userDetails.getPhone() != null) {
            user.setPhone(userDetails.getPhone().isEmpty() ? null : userDetails.getPhone());
        }
        
        // Only update address if provided (can be empty string to clear)
        if (userDetails.getAddress() != null) {
            user.setAddress(userDetails.getAddress().isEmpty() ? null : userDetails.getAddress());
        }
        
        // Update new fields
        if (userDetails.getGender() != null) {
            user.setGender(userDetails.getGender().isEmpty() ? null : userDetails.getGender());
        }
        
        if (userDetails.getDateOfBirth() != null) {
            user.setDateOfBirth(userDetails.getDateOfBirth());
        }
        
        if (userDetails.getAlternatePhone() != null) {
            user.setAlternatePhone(userDetails.getAlternatePhone().isEmpty() ? null : userDetails.getAlternatePhone());
        }
        
        if (userDetails.getProfilePhoto() != null) {
            user.setProfilePhoto(userDetails.getProfilePhoto().isEmpty() ? null : userDetails.getProfilePhoto());
        }
        
        // Update structured address fields
        if (userDetails.getHouseDoorNo() != null) {
            user.setHouseDoorNo(userDetails.getHouseDoorNo().isEmpty() ? null : userDetails.getHouseDoorNo());
        }
        
        if (userDetails.getStreetArea() != null) {
            user.setStreetArea(userDetails.getStreetArea().isEmpty() ? null : userDetails.getStreetArea());
        }
        
        if (userDetails.getCity() != null) {
            user.setCity(userDetails.getCity().isEmpty() ? null : userDetails.getCity());
        }
        
        if (userDetails.getDistrict() != null) {
            user.setDistrict(userDetails.getDistrict().isEmpty() ? null : userDetails.getDistrict());
        }
        
        if (userDetails.getState() != null) {
            user.setState(userDetails.getState().isEmpty() ? null : userDetails.getState());
        }
        
        if (userDetails.getPincode() != null) {
            user.setPincode(userDetails.getPincode().isEmpty() ? null : userDetails.getPincode());
        }
        
        if (userDetails.getLandmark() != null) {
            user.setLandmark(userDetails.getLandmark().isEmpty() ? null : userDetails.getLandmark());
        }
        
        // Update isActive status (Account Status: ACTIVE/INACTIVE)
        // CRITICAL: Prevent deactivating Admin accounts
        if (userDetails.getIsActive() != null) {
            if (user.getRole() == User.UserRole.admin && !userDetails.getIsActive()) {
                throw new IllegalArgumentException("Cannot deactivate admin accounts. Admin accounts always have full access.");
            }
            user.setIsActive(userDetails.getIsActive());
        }
        
        return userRepository.save(user);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        
        // Prevent deleting default admin
        if (user.getUsername().equals("admin")) {
            throw new IllegalArgumentException("Cannot delete default admin account");
        }
        
        userRepository.deleteById(id);
    }
    
    @Transactional
    public User activateUser(Long id) {
        User user = getUserById(id);
        user.setIsActive(true);
        return userRepository.save(user);
    }
    
    @Transactional
    public User deactivateUser(Long id) {
        User user = getUserById(id);
        
        // CRITICAL: Prevent deactivating Admin accounts (any admin, not just default)
        // Admin accounts always have full access regardless of isActive status
        if (user.getRole() == User.UserRole.admin) {
            throw new IllegalArgumentException("Cannot deactivate admin accounts. Admin accounts always have full access.");
        }
        
        // Only Seller and Buyer accounts can be deactivated
        user.setIsActive(false);
        return userRepository.save(user);
    }
}

