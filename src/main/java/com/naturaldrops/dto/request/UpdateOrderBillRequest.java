package com.naturaldrops.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class UpdateOrderBillRequest {
    
    @NotNull(message = "Final bill amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Final bill amount must be greater than 0")
    private BigDecimal finalBillAmount;
    
    private String billingNotes;
    
    private String billedBy; // Seller username
}

