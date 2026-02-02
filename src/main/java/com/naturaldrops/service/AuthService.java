package com.naturaldrops.service;

import com.naturaldrops.dto.request.ChangePasswordRequest;
import com.naturaldrops.dto.request.ForgotPasswordRequest;
import com.naturaldrops.dto.request.LoginRequest;
import com.naturaldrops.dto.request.RefreshTokenRequest;
import com.naturaldrops.dto.request.RegisterRequest;
import com.naturaldrops.dto.request.ResetPasswordRequest;
import com.naturaldrops.dto.response.LoginResponse;
import com.naturaldrops.dto.response.RefreshTokenResponse;
import com.naturaldrops.entity.PasswordResetToken;
import com.naturaldrops.entity.RefreshToken;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.AccountStatusException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.PasswordResetTokenRepository;
import com.naturaldrops.repository.RefreshTokenRepository;
import com.naturaldrops.repository.UserRepository;
import com.naturaldrops.util.JwtTokenProvider;
import com.naturaldrops.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecureRandom random = new SecureRandom();
    
    @Transactional
    public User register(RegisterRequest request) {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🔵 [AuthService] ===== REGISTRATION ATTEMPT STARTED ======");
        log.info("   Username: {}", request.getUsername());
        log.info("   Email: {}", request.getEmail() != null ? request.getEmail() : "N/A");
        log.info("   Role: {}", request.getRole());
        log.info("   Timestamp: {}", LocalDateTime.now());
        log.info("═══════════════════════════════════════════════════════");
        
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("❌ [AuthService] Registration failed - Username already exists: {}", request.getUsername());
            throw new IllegalArgumentException("Username already exists");
        }
        log.info("✅ [AuthService] Username available");
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        
        // New fields
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setAlternatePhone(request.getAlternatePhone());
        user.setProfilePhoto(request.getProfilePhoto());
        
        // Structured address fields
        user.setHouseDoorNo(request.getHouseDoorNo());
        user.setStreetArea(request.getStreetArea());
        user.setCity(request.getCity());
        user.setDistrict(request.getDistrict());
        user.setState(request.getState());
        user.setPincode(request.getPincode());
        user.setLandmark(request.getLandmark());
        
        // Keep old address field for backward compatibility
        // Build address string from structured fields if old address not provided
        if (request.getAddress() == null || request.getAddress().trim().isEmpty()) {
            StringBuilder addressBuilder = new StringBuilder();
            if (request.getHouseDoorNo() != null) addressBuilder.append(request.getHouseDoorNo()).append(", ");
            if (request.getStreetArea() != null) addressBuilder.append(request.getStreetArea()).append(", ");
            if (request.getCity() != null) addressBuilder.append(request.getCity()).append(", ");
            if (request.getDistrict() != null) addressBuilder.append(request.getDistrict()).append(", ");
            if (request.getState() != null) addressBuilder.append(request.getState()).append(" - ");
            if (request.getPincode() != null) addressBuilder.append(request.getPincode());
            if (request.getLandmark() != null && !request.getLandmark().trim().isEmpty()) {
                addressBuilder.append(" (Landmark: ").append(request.getLandmark()).append(")");
            }
            user.setAddress(addressBuilder.toString().trim());
        } else {
            user.setAddress(request.getAddress());
        }
        
        user.setCreatedAt(LocalDateTime.now());
        user.setCreatedBy(request.getUsername());
        
        // Set user status based on role
        // Buyers require admin approval (PENDING), Admin/Seller are auto-approved
        if (request.getRole() == User.UserRole.buyer) {
            user.setStatus(User.UserStatus.PENDING);
        } else {
            // Admin and Seller accounts are auto-approved
            user.setStatus(User.UserStatus.APPROVED);
        }
        
        // All new users start as INACTIVE - admin must activate them
        user.setIsActive(false);
        log.info("🔍 [AuthService] Setting new user as INACTIVE (isActive = false)");
        log.info("   Admin must activate account before user can access application");
        
        User savedUser = userRepository.save(user);
        log.info("═══════════════════════════════════════════════════════");
        log.info("✅ [AuthService] ===== REGISTRATION SUCCESSFUL ======");
        log.info("   User ID: {}", savedUser.getId());
        log.info("   Username: {}", savedUser.getUsername());
        log.info("   Role: {}", savedUser.getRole());
        log.info("   Status: {} (Buyer=PENDING, Admin/Seller=APPROVED)", savedUser.getStatus());
        log.info("   isActive: {} (All new users start as INACTIVE)", savedUser.getIsActive());
        log.info("   Timestamp: {}", LocalDateTime.now());
        log.info("═══════════════════════════════════════════════════════");
        
        return savedUser;
    }
    
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🔵 [AuthService] ===== LOGIN ATTEMPT STARTED ======");
        log.info("   Username: {}", request.getUsername());
        log.info("   Timestamp: {}", java.time.LocalDateTime.now());
        log.info("═══════════════════════════════════════════════════════");
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("❌ [AuthService] Login failed - User not found: {}", request.getUsername());
                    return new UnauthorizedException("Invalid username or password");
                });
        
        log.info("✅ [AuthService] User found in database");
        log.info("   User ID: {}", user.getId());
        log.info("   Username: {}", user.getUsername());
        log.info("   Role: {}", user.getRole());
        log.info("   Email: {}", user.getEmail() != null ? user.getEmail() : "N/A");
        log.info("   Current isActive: {}", user.getIsActive());
        log.info("   Current status: {}", user.getStatus());
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("❌ [AuthService] Login failed - Invalid password for user: {}", request.getUsername());
            throw new UnauthorizedException("Invalid username or password");
        }
        
        log.info("✅ [AuthService] Password verification passed");
        
        // CRITICAL: Check isActive FIRST (primary control for ACTIVE/DEACTIVE)
        // Admin always has full access, regardless of isActive status
        // Only Seller and Buyer accounts are subject to isActive check
        log.info("🔍 [AuthService] Checking account status and permissions...");
        log.info("   User role: {}", user.getRole());
        log.info("   Is Admin: {}", user.getRole() == User.UserRole.admin);
        
        if (user.getRole() != User.UserRole.admin) {
            log.info("🔍 [AuthService] Non-admin user - checking isActive status...");
            // For Seller and Buyer: check isActive status first
            if (user.getIsActive() == null) {
                // Legacy users without isActive - set to true for backward compatibility
                log.info("⚠️ [AuthService] Legacy user without isActive - setting to true for backward compatibility");
                user.setIsActive(true);
                userRepository.save(user);
                log.info("✅ [AuthService] Updated legacy user isActive to true");
            } else if (!user.getIsActive()) {
                // Account is DEACTIVE - block login immediately
                log.warn("❌ [AuthService] Account is DEACTIVE (isActive = false) - blocking login");
                log.warn("   Username: {}", user.getUsername());
                log.warn("   Role: {}", user.getRole());
                throw new AccountStatusException(
                    "Your account is deactivated. Please contact customer care.",
                    "INACTIVE"
                );
            }
            log.info("✅ [AuthService] Account is ACTIVE (isActive = true)");
            
            // If we reach here, isActive = true (account is ACTIVE)
            // Auto-approve status if it's PENDING (since admin activated the account)
            User.UserStatus status = user.getStatus();
            log.info("🔍 [AuthService] Checking user status: {}", status);
            
            if (status == null) {
                // Legacy users without status - treat as APPROVED
                log.info("⚠️ [AuthService] Legacy user without status - setting to APPROVED");
                user.setStatus(User.UserStatus.APPROVED);
                userRepository.save(user);
                log.info("✅ [AuthService] Updated legacy user status to APPROVED");
            } else if (status == User.UserStatus.PENDING) {
                // Account is ACTIVE but status is PENDING - auto-approve
                log.info("🔄 [AuthService] Auto-approving status for active user: {}", user.getUsername());
                log.info("   Previous status: PENDING");
                log.info("   New status: APPROVED");
                user.setStatus(User.UserStatus.APPROVED);
                userRepository.save(user);
                log.info("✅ [AuthService] Status auto-approved successfully");
            } else if (status == User.UserStatus.REJECTED || status == User.UserStatus.BLOCKED) {
                // Even if active, REJECTED/BLOCKED status takes precedence
                log.warn("❌ [AuthService] Account access restricted - status: {}", status);
                log.warn("   Username: {}", user.getUsername());
                log.warn("   Role: {}", user.getRole());
                log.warn("   isActive: {}", user.getIsActive());
                throw new AccountStatusException(
                    "Your account access is restricted. Please contact Customer Service for support.",
                    status.name()
                );
            }
            log.info("✅ [AuthService] Account status check passed");
        } else {
            log.info("🔍 [AuthService] Admin user - bypassing isActive check");
            // Admin: ensure isActive and status are set (for consistency) but don't block access
            if (user.getIsActive() == null) {
                log.info("⚠️ [AuthService] Admin user without isActive - setting to true");
                user.setIsActive(true);
            }
            if (user.getStatus() == null) {
                log.info("⚠️ [AuthService] Admin user without status - setting to APPROVED");
                user.setStatus(User.UserStatus.APPROVED);
            }
            if (user.getIsActive() == null || user.getStatus() == null) {
                userRepository.save(user);
                log.info("✅ [AuthService] Updated admin user status fields");
            }
            log.info("✅ [AuthService] Admin user - full access granted");
        }
        
        // Only APPROVED AND ACTIVE users can proceed (Admin is always allowed)
        log.info("🔍 [AuthService] Proceeding with token generation...");
        
        // Revoke any existing refresh tokens for this user (single active session)
        log.info("🔄 [AuthService] Revoking existing refresh tokens for user: {}", user.getId());
        revokeUserRefreshTokens(user.getId());
        log.info("✅ [AuthService] Existing refresh tokens revoked");
        
        // CRITICAL: Refresh user entity from database to ensure latest isActive status
        // This ensures any recent admin changes to account status are reflected
        log.info("🔄 [AuthService] Refreshing user entity from database to get latest status...");
        Long userId = user.getId(); // Store ID before reassignment for lambda
        user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [AuthService] User not found after refresh - ID: {}", userId);
                    return new UnauthorizedException("User not found");
                });
        log.info("✅ [AuthService] User entity refreshed");
        log.info("   Final isActive: {}", user.getIsActive());
        log.info("   Final status: {}", user.getStatus());
        
        // Generate tokens
        log.info("🔑 [AuthService] Generating JWT tokens...");
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenString = jwtTokenProvider.generateRefreshToken(user);
        log.info("✅ [AuthService] JWT tokens generated successfully");
        
        // Save new refresh token to database
        log.info("💾 [AuthService] Saving refresh token to database...");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(refreshTokenString);
        refreshToken.setExpiryTime(LocalDateTime.now().plusDays(7)); // 7 days to match jwt.refresh-token-expiration
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        log.info("✅ [AuthService] Refresh token saved to database");
        
        // Log user status for debugging
        log.info("═══════════════════════════════════════════════════════");
        log.info("✅ [AuthService] ===== LOGIN SUCCESSFUL ======");
        log.info("   Username: {}", user.getUsername());
        log.info("   User ID: {}", user.getId());
        log.info("   Role: {}", user.getRole());
        log.info("   isActive: {}", user.getIsActive());
        log.info("   Status: {}", user.getStatus());
        log.info("   Email: {}", user.getEmail() != null ? user.getEmail() : "N/A");
        log.info("   Timestamp: {}", LocalDateTime.now());
        log.info("═══════════════════════════════════════════════════════");
        
        return new LoginResponse(accessToken, refreshTokenString, user, user.getRole().name());
    }
    
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        // Validate refresh token JWT
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        
        // Check if refresh token exists in database and is not revoked
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
        
        if (refreshToken.getRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        
        if (refreshToken.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        
        // Get user
        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        
        // Optionally generate new refresh token (refresh token rotation)
        String newRefreshTokenString = jwtTokenProvider.generateRefreshToken(user);
        
        // Revoke old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        
        // Save new refresh token
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setToken(newRefreshTokenString);
        newRefreshToken.setExpiryTime(LocalDateTime.now().plusDays(7)); // 7 days to match jwt.refresh-token-expiration
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);
        
        return new RefreshTokenResponse(newAccessToken, newRefreshTokenString);
    }
    
    @Transactional
    public void logout(Long userId) {
        // Revoke all refresh tokens for the user
        revokeUserRefreshTokens(userId);
    }
    
    @Transactional
    public void revokeUserRefreshTokens(Long userId) {
        // Delete all existing refresh tokens for this user
        refreshTokenRepository.deleteByUserId(userId);
    }
    
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new UnauthorizedException("The current password you entered is incorrect. Please try again.");
        }
        
        // Validate new password matches confirm password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        
        // Validate password strength
        PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePasswordStrength(request.getNewPassword());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }
        
        // Encode and update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        // Security best practice: Don't reveal if email exists
        // Use findFirstByEmail to handle cases where multiple users have the same email
        Optional<User> userOptional = userRepository.findFirstByEmail(request.getEmail());
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Delete any existing unused tokens for this user
            passwordResetTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            
            // Generate token and OTP
            String token = UUID.randomUUID().toString();
            String otp = generateOtp();
            
            // Create reset token entity
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setToken(token);
            resetToken.setOtp(otp);
            resetToken.setExpiryTime(LocalDateTime.now().plusMinutes(15));
            resetToken.setUsed(false);
            
            passwordResetTokenRepository.save(resetToken);
            
            // Get user's name for personalized email (prefer fullName, fallback to username)
            String userName = (user.getFullName() != null && !user.getFullName().trim().isEmpty()) 
                    ? user.getFullName() 
                    : user.getUsername();
            
            // Send email with both token and OTP, including user's name for personalization
            // If email fails, still return success (security best practice - don't reveal if email exists)
            // The token/OTP is still created, so user can reset password if they know the OTP
            boolean emailSent = emailService.sendPasswordResetToken(user.getEmail(), userName, token, otp);
            if (!emailSent) {
                // SECURITY: Do NOT log token or OTP - they are sensitive
                log.warn("Password reset token created for user {} but email failed to send. " +
                        "Token and OTP are available in database but email delivery failed.", user.getEmail());
            }
        }
        
        // Always return success message (security best practice - don't reveal if email exists)
    }
    
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Validate that either token or OTP is provided
        if ((request.getToken() == null || request.getToken().trim().isEmpty()) &&
            (request.getOtp() == null || request.getOtp().trim().isEmpty())) {
            throw new IllegalArgumentException("Either token or OTP is required");
        }
        
        // Find token by token or OTP
        Optional<PasswordResetToken> tokenOptional = Optional.empty();
        if (request.getToken() != null && !request.getToken().trim().isEmpty()) {
            tokenOptional = passwordResetTokenRepository.findByToken(request.getToken());
        } else if (request.getOtp() != null && !request.getOtp().trim().isEmpty()) {
            tokenOptional = passwordResetTokenRepository.findByOtp(request.getOtp());
        }
        
        PasswordResetToken resetToken = tokenOptional
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset token/OTP"));
        
        // Check if token is already used
        if (resetToken.getUsed()) {
            throw new UnauthorizedException("This reset token/OTP has already been used");
        }
        
        // Check if token is expired
        if (resetToken.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Reset token/OTP has expired");
        }
        
        // Validate new password matches confirm password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        
        // Validate password strength
        PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePasswordStrength(request.getNewPassword());
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }
        
        // Get user and update password
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        
        // Clean up expired tokens
        passwordResetTokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }
    
    private String generateOtp() {
        // Generate 6-digit OTP
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}

