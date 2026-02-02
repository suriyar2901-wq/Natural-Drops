package com.naturaldrops.exception;

/**
 * Exception thrown when user account status prevents login/access
 * Examples: PENDING, REJECTED, BLOCKED status
 */
public class AccountStatusException extends RuntimeException {
    private final String status;
    
    public AccountStatusException(String message, String status) {
        super(message);
        this.status = status;
    }
    
    public String getStatus() {
        return status;
    }
}

