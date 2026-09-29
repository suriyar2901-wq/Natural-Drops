package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shop_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false, unique = true)
    private Long sellerUserId;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Column(name = "alt_mobile", length = 20)
    private String altMobile;

    @Column(length = 100)
    private String email;

    @Column(name = "qr_data", columnDefinition = "TEXT")
    private String qrData;
}
