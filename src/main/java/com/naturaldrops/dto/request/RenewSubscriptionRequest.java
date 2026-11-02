package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class RenewSubscriptionRequest {
    private String plan;
    private String method;
    private String gatewayRef;
    private String paidAt;
    private String note;
}
