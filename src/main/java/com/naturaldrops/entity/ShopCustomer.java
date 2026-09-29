package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "buyer_user_id")
    private Long buyerUserId;

    @Column(name = "customer_code", nullable = false, length = 20)
    private String customerCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 100)
    private String house;

    @Column(length = 100)
    private String area;

    @Column(length = 100)
    private String city;

    @Column(length = 10)
    private String pin;

    @Column(length = 200)
    private String note;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal money = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer emptyCans = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (money == null) {
            money = BigDecimal.ZERO;
        }
        if (emptyCans == null) {
            emptyCans = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
