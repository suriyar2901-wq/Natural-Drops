package com.naturaldrops.util;

import lombok.AllArgsConstructor;
import lombok.Data;

public class PasswordValidator {
    
    private static final int MIN_LENGTH = 8;
    
    @Data
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String message;
    }
    
    public static ValidationResult validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, "Password is required");
        }
        
        if (password.length() < MIN_LENGTH) {
            return new ValidationResult(false, "Password must be at least " + MIN_LENGTH + " characters long");
        }
        
        boolean hasLetter = false;
        boolean hasNumber = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasNumber = true;
            }
        }
        
        if (!hasLetter) {
            return new ValidationResult(false, "Password must contain at least one letter");
        }
        
        if (!hasNumber) {
            return new ValidationResult(false, "Password must contain at least one number");
        }
        
        return new ValidationResult(true, "Password is valid");
    }
}

