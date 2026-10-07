package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "seller_can_stock_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerCanStockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "change_amount", nullable = false)
    private Integer changeAmount;

    @Column(length = 200)
    private String copy;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
