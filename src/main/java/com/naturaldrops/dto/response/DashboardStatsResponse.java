package com.naturaldrops.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Long totalOrders;
    private Long pendingOrders;
    private Long deliveredOrders;
    private BigDecimal totalRevenue;
    private Long productsCount;
    private String dateRangeLabel; // e.g., "Jan 1 - Jan 31, 2026" or "All Time"
    private Long todayOrders; // Count of today's orders (only when showing all-time stats)
}

