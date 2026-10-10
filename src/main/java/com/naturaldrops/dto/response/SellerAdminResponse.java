package com.naturaldrops.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SellerAdminResponse {
    private Long id;
    private String sellerCode;
    private String companyCode;
    private String ownerName;
    private String gender;
    private LocalDate dateOfBirth;
    private String aadhaarNumber;
    private String mobile;
    private String alternateMobile;
    private String email;
    private String businessName;
    private String businessAddress;
    private String area;
    private String city;
    private String pincode;
    private String accountStatus;
    private Boolean loginActive;
    private String deactivationReason;
    private String adminNote;
    private LocalDateTime createdAt;
    private String createdBy;
    private String username;
    private String inviteLink;
    private String whatsappUrl;
    private String smsUrl;
    private Boolean emailSent;

    private Long subscriptionId;
    private String plan;
    private BigDecimal amount;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private String paymentStatus;
    private String subscriptionStatus;
    private long daysRemaining;

    private List<SellerPaymentResponse> payments = new ArrayList<SellerPaymentResponse>();
}
