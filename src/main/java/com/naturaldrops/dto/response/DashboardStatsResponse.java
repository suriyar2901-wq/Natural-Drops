package com.naturaldrops.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Long totalOrders;
    private Long pendingOrders;
    private Long deliveredOrders;
    private BigDecimal totalRevenue;
    private BigDecimal paidEarnings;
    private BigDecimal partialCollected;
    private BigDecimal balanceDue;
    private Long productsCount;
    private String dateRangeLabel; // e.g., "Jan 1 - Jan 31, 2026" or "All Time"
    private Long todayOrders; // Count of today's orders (only when showing all-time stats)
    private List<MonthlyRevenuePoint> monthlyRevenue = new ArrayList<MonthlyRevenuePoint>();
    private List<MonthlyRevenuePoint> earningsGraph = new ArrayList<MonthlyRevenuePoint>();
}

