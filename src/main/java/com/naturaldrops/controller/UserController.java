package com.naturaldrops.controller;

import com.naturaldrops.dto.request.ChangePasswordRequest;
import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.AuthService;
import com.naturaldrops.service.UserService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class UserController {
    
    private final UserService userService;
    private final AuthService authService;
    
    /**
     * Helper method to check if current user is ADMIN
     * Returns true only if user is authenticated and has ADMIN role
     */
    private boolean isAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        return currentUser != null && currentUser.getRole() == User.UserRole.admin;
    }
    
    /**
     * Helper method to check authentication and admin role
     * Throws UnauthorizedException if not authenticated or not admin
     */
    private void requireAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (currentUser.getRole() != User.UserRole.admin) {
            throw new UnauthorizedException("Access denied. Only administrators can access this resource.");
        }
    }
    
    /**
     * Helper method to get current authenticated user
     * Throws UnauthorizedException if not authenticated
     */
    private User getCurrentUser(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return currentUser;
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(HttpServletRequest request) {
        requireAdmin(request);
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<User>> getUserByUsername(@PathVariable String username, HttpServletRequest request) {
        requireAdmin(request);
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByRole(@PathVariable User.UserRole role, HttpServletRequest request) {
        requireAdmin(request);
        List<User> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user, HttpServletRequest request) {
        requireAdmin(request);
        User currentUser = getCurrentUser(request);
        String createdBy = currentUser.getUsername();
        User createdUser = userService.createUser(user, createdBy);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", createdUser));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long id, @RequestBody User user, HttpServletRequest request) {
        requireAdmin(request);
        User updatedUser = userService.updateUser(id, user);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        User currentUser = getCurrentUser(httpRequest);
        authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
    
    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<User>> activateUser(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        User updatedUser = userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User activated successfully", updatedUser));
    }
    
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<User>> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        User updatedUser = userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", updatedUser));
    }
}

