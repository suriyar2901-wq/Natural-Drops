package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "regular_buyer_plans", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"seller_user_id", "buyer_user_id"})
})
@Data
@NoArgsConstructor
public class RegularBuyerPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_prompt_date")
    private LocalDate lastPromptDate;

    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;

    /** Seller-chosen time when the daily create-order message should appear. */
    @Column(name = "prompt_time")
    private LocalTime promptTime;

    /** Time the regular order should be delivered. */
    @Column(name = "delivery_time")
    private LocalTime deliveryTime;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
