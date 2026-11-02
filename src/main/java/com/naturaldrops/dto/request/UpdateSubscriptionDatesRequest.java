package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class UpdateSubscriptionDatesRequest {
    private String startDate;
    private String expiryDate;
}
