package com.naturaldrops.dto.request;

import lombok.Data;

@Data
public class CreateSellerRequest {
    private String ownerName;
    private String username;
    private String mobile;
    private String alternateMobile;
    private String email;
    private String businessName;
    private String businessAddress;
    private String area;
    private String city;
    private String pincode;
    private String plan;
}
