package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class RecordSellerPaymentRequest {
    private String method;
    private String gatewayRef;
    private String receivedAmount;
    private String paidAt;
    private String note;
    private String plan;
}
