package com.naturaldrops.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlatformDashboardResponse {
    private String period;
    private long totalSellers;
    private long activeSubscribers;
    private BigDecimal revenueThisMonth = BigDecimal.ZERO;
    private BigDecimal yetToReceive = BigDecimal.ZERO;
    private BigDecimal revenueToday = BigDecimal.ZERO;
    private BigDecimal revenueLastMonth = BigDecimal.ZERO;
    private BigDecimal expectedRevenue = BigDecimal.ZERO;
    private String activeRate;

    private long activeSubscriptions;
    private long expiringSoon;
    private long expired;
    private long paymentPending;
    private long deactivatedAccounts;

    private long newSellers;
    private long renewed;
    private long notRenewed;
    private String renewalRate;

    private long totalBuyers;
    private long ordersToday;
    private long ordersThisMonth;
    private long activeSellersToday;

    private List<SellerPaymentResponse> recentPayments = new ArrayList<SellerPaymentResponse>();
    private List<String> attentionItems = new ArrayList<String>();
}
