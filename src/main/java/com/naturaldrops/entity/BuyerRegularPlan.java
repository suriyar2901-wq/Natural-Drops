package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "buyer_regular_plans")
@Data
@NoArgsConstructor
public class BuyerRegularPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false, unique = true)
    private Long buyerId;

    @Column(name = "seller_user_id")
    private Long sellerUserId;

    @Column(nullable = false)
    private boolean paused = false;

    @Column(name = "delivery_time", length = 5)
    private String deliveryTime;

    /** JavaScript weekdays: 0=Sunday ... 6=Saturday, comma separated. */
    @Column(name = "week_days", length = 20)
    private String weekDays;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    private Double latitude;
    private Double longitude;

    /** Delivery date the scheduler already created an order for. */
    @Column(name = "last_created_for")
    private LocalDate lastCreatedFor;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BuyerRegularItem> items = new ArrayList<BuyerRegularItem>();
}
