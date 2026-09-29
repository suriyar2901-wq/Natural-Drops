package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class DeactivateSellerRequest {
    private String reason;
    private String adminNote;
}
