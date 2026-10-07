package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_can_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerCanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false, unique = true)
    private Long sellerUserId;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock = 0;

    @Column(name = "deposit_per_can", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositPerCan = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer damaged = 0;

    @Column(nullable = false)
    private Integer missing = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
        if (totalStock == null) {
            totalStock = 0;
        }
        if (depositPerCan == null) {
            depositPerCan = BigDecimal.ZERO;
        }
        if (damaged == null) {
            damaged = 0;
        }
        if (missing == null) {
            missing = 0;
        }
    }
}
