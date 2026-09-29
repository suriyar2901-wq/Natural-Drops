package com.naturaldrops.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SellerPaymentResponse {
    private Long id;
    private String transactionCode;
    private Long sellerId;
    private String sellerCode;
    private String sellerName;
    private String businessName;
    private String plan;
    private BigDecimal amount;
    private BigDecimal planPriceSnapshot;
    private String method;
    private String status;
    private String gatewayRef;
    private String note;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
