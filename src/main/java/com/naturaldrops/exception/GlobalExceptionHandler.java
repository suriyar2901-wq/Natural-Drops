package com.naturaldrops.exception;

import com.naturaldrops.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(UnauthorizedException ex) {
        // Check if it's an access denied message (403) vs authentication required (401)
        if (ex.getMessage() != null && 
            (ex.getMessage().contains("Access denied") || 
             ex.getMessage().contains("Only administrators") ||
             ex.getMessage().contains("permission"))) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ex.getMessage()));
        }
        // Default to 401 for authentication issues
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }
    
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountStatus(AccountStatusException ex) {
        // Return 403 Forbidden with account status message and code
        // Frontend will handle this to show customer service contact info
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        // Add status code to response for frontend to identify inactive accounts
        if (ex.getStatus() != null && ex.getStatus().equals("INACTIVE")) {
            // For inactive accounts, frontend will show dedicated inactive screen
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .header("X-Account-Status", "INACTIVE")
                    .body(response);
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .header("X-Account-Status", ex.getStatus() != null ? ex.getStatus() : "UNKNOWN")
                .body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, "Validation failed", errors));
    }
    
    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<ApiResponse<Object>> handleClientAbortException(ClientAbortException ex) {
        // Client disconnected - this is normal, don't log as error
        // Return null response since client is already gone
        log.debug("Client disconnected: {}", ex.getMessage());
        return null;
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        // Check if it's a client disconnect (broken pipe) - don't log as error
        if (ex instanceof ClientAbortException) {
            log.debug("Client disconnected: {}", ex.getMessage());
            return null;
        }
        
        // Check for broken pipe in cause chain
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IOException && 
                cause.getMessage() != null && 
                cause.getMessage().contains("Broken pipe")) {
                log.debug("Client disconnected (broken pipe): {}", cause.getMessage());
                return null;
            }
            cause = cause.getCause();
        }
        
        // For real errors, log and return error response
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An error occurred: " + ex.getMessage()));
    }
}

