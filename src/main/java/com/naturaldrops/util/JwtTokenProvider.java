package com.naturaldrops.util;

import com.naturaldrops.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {
    
    @Value("${jwt.secret:naturaldrops-secret-key-change-in-production-minimum-256-bits}")
    private String jwtSecret;
    
    @Value("${jwt.access-token-expiration:7200000}") // 2 hours in milliseconds
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}") // 7 days in milliseconds
    private long refreshTokenExpiration;
    
    @Value("${jwt.clock-skew-tolerance:300000}") // 5 minutes in milliseconds
    private long clockSkewTolerance;
    
    private SecretKey getSigningKey() {
        // Ensure secret is at least 256 bits (32 characters) for HS256
        String secret = jwtSecret;
        if (secret.length() < 32) {
            StringBuilder sb = new StringBuilder(secret);
            for (int i = 0; i < 32 - secret.length(); i++) {
                sb.append("0");
            }
            secret = sb.toString();
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole().name());
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", "refresh");
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    private io.jsonwebtoken.JwtParserBuilder getJwtParserBuilder() {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .setAllowedClockSkewSeconds(clockSkewTolerance / 1000); // Convert milliseconds to seconds
    }
    
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getJwtParserBuilder()
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Error extracting username from token", e);
            return null;
        }
    }
    
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getJwtParserBuilder()
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("Error extracting user ID from token", e);
            return null;
        }
    }
    
    public String getRoleFromToken(String token) {
        try {
            Claims claims = getJwtParserBuilder()
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("Error extracting role from token", e);
            return null;
        }
    }
    
    public boolean validateToken(String token) {
        try {
            getJwtParserBuilder()
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error validating JWT token", e);
            return false;
        }
    }
    
    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = getJwtParserBuilder()
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Error extracting expiration date from token", e);
            return null;
        }
    }
}

