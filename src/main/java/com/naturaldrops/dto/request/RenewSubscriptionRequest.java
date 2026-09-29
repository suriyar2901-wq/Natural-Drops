package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class RenewSubscriptionRequest {
    private String plan;
    private String paidAt;
    private String note;
}
