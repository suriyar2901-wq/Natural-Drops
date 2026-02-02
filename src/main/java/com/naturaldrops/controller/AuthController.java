package com.naturaldrops.controller;

import com.naturaldrops.dto.request.ForgotPasswordRequest;
import com.naturaldrops.dto.request.LoginRequest;
import com.naturaldrops.dto.request.RefreshTokenRequest;
import com.naturaldrops.dto.request.RegisterRequest;
import com.naturaldrops.dto.request.ResetPasswordRequest;
import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.dto.response.LoginResponse;
import com.naturaldrops.dto.response.RefreshTokenResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Registration successful", user));
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", loginResponse));
    }
    
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser != null) {
            authService.logout(currentUser.getId());
        }
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
    
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.ok(ApiResponse.error("Not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success(currentUser));
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        // Always return success message (security best practice - don't reveal if email exists)
        return ResponseEntity.ok(ApiResponse.success("If an account exists with this email, password reset instructions have been sent", null));
    }
    
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }
}

