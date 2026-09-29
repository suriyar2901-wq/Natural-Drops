package com.naturaldrops.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SellerSubscriptionAccessResponse {
    private boolean canWork;
    private boolean subscribed;
    private boolean showExpiryReminder;
    private String status;
    private String reminderMessage;
    private String plan;
    private BigDecimal amount;
    private BigDecimal monthlyAmount;
    private BigDecimal yearlyAmount;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private Long daysRemaining;
    private String businessName;
}
