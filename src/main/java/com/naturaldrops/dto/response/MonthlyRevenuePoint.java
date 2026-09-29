package com.naturaldrops.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyRevenuePoint {
    private String month;
    private String monthKey;
    private BigDecimal orderRevenue;
    private BigDecimal subscriptionRevenue;
    private BigDecimal totalRevenue;
    private Long orderCount;
}
