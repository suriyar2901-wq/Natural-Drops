package com.naturaldrops.config;

import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.UserRepository;
import com.naturaldrops.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class JwtTokenFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    // Public endpoints that don't require JWT authentication
    // These endpoints must work without Authorization header
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/refresh-token",  // Refresh token endpoint uses refresh token, not access token
        "/api/settings/customer-contact-number",  // GET only - public contact info
        "/api/settings/customer-support-email"   // GET only - public contact info
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String contextPath = request.getContextPath();
        
        // Remove context path if present (for deployed applications)
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        
        log.debug("JWT Filter - Request: {} {}, Path: {}, Context: {}", method, requestPath, contextPath);
        
        // Skip JWT validation for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("Skipping JWT validation for OPTIONS request");
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip JWT validation for public endpoints
        // Do NOT attempt to read Authorization header for these endpoints
        if (isPublicEndpoint(requestPath, method)) {
            log.info("✅ Skipping JWT validation for public endpoint: {} {}", method, requestPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("🔒 JWT validation required for: {} {}", method, requestPath);
        
        try {
            String token = extractTokenFromRequest(request);
            
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new UnauthorizedException("Invalid or expired access token");
            }
            
            // Extract user info from token and set in request attribute
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            if (userId == null) {
                throw new UnauthorizedException("Invalid token: user ID not found");
            }
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            
            // Check if user account is active (admin-controlled activation)
            // CRITICAL: Admin always has full access, regardless of isActive status
            // Only Seller and Buyer accounts are subject to isActive check
            if (user.getRole() != User.UserRole.admin) {
                // For Seller and Buyer: check isActive status
                if (user.getIsActive() == null) {
                    // Legacy users without isActive - set to true for backward compatibility
                    user.setIsActive(true);
                    userRepository.save(user);
                } else if (!user.getIsActive()) {
                    throw new UnauthorizedException("Your account is deactivated. Please contact customer care.");
                }
                
                // Check if user status is APPROVED (only for Seller/Buyer)
                if (user.getStatus() != null && user.getStatus() != User.UserStatus.APPROVED) {
                    throw new UnauthorizedException("Your account access is restricted. Please contact Customer Service for support.");
                }
            } else {
                // Admin: ensure isActive is set (for consistency) but don't block access
                if (user.getIsActive() == null) {
                    user.setIsActive(true);
                    userRepository.save(user);
                }
                // Admin always has access regardless of status
            }
            
            // Store user in request attribute for controllers to access
            request.setAttribute("currentUser", user);
            
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                String.format("{\"success\":false,\"message\":\"%s\"}", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error processing JWT token", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Authentication failed\"}"
            );
        }
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * Check if the endpoint is public and doesn't require JWT authentication
     * Uses exact path matching for security
     * For settings endpoints, only GET requests are public
     */
    private boolean isPublicEndpoint(String path, String method) {
        // Normalize path (remove query string if present)
        String normalizedPath = path;
        if (path.contains("?")) {
            normalizedPath = path.substring(0, path.indexOf("?"));
        }
        
        // Ensure path starts with /
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        
        // Check exact match for public endpoints
        for (String publicEndpoint : PUBLIC_ENDPOINTS) {
            // Exact match
            if (normalizedPath.equals(publicEndpoint)) {
                // For settings endpoints, only allow GET requests to be public
                if (normalizedPath.startsWith("/api/settings/")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        log.debug("✅ Public endpoint match (exact): {} {} == {}", method, normalizedPath, publicEndpoint);
                        return true;
                    } else {
                        log.debug("❌ Settings endpoint requires authentication for non-GET: {} {}", method, normalizedPath);
                        return false;
                    }
                }
                log.debug("✅ Public endpoint match (exact): {} {} == {}", method, normalizedPath, publicEndpoint);
                return true;
            }
            // Also check if it's a sub-path (e.g., /api/auth/login/extra)
            if (normalizedPath.startsWith(publicEndpoint + "/")) {
                log.debug("✅ Public endpoint match (prefix): {} {} starts with {}", method, normalizedPath, publicEndpoint);
                return true;
            }
        }
        
        log.debug("❌ Not a public endpoint: {} {}", method, normalizedPath);
        return false;
    }
}

