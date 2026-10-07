package com.naturaldrops.service;

import com.naturaldrops.dto.response.DashboardStatsResponse;
import com.naturaldrops.dto.response.MonthlyRevenuePoint;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerPayment;
import com.naturaldrops.entity.SellerSubscription;
import com.naturaldrops.entity.User;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.MenuItemRepository;
import com.naturaldrops.repository.SellerPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final SellerPaymentRepository sellerPaymentRepository;
    private final SellerNetworkService sellerNetworkService;
    
    /**
     * Get dashboard statistics for a specific date range
     * @param fromDate Start date (inclusive), null for all time
     * @param toDate End date (inclusive), null for all time
     * @return Dashboard statistics
     */
    public DashboardStatsResponse getDashboardStats(LocalDate fromDate, LocalDate toDate, User currentUser) {
        List<Order> orders;
        String dateRangeLabel;
        
        if (fromDate != null && toDate != null) {
            // Filter by date range
            LocalDateTime startDateTime = fromDate.atStartOfDay();
            LocalDateTime endDateTime = toDate.atTime(23, 59, 59);
            orders = sellerNetworkService.scopeOrders(
                    orderRepository.findOrdersBetweenDates(startDateTime, endDateTime),
                    currentUser
            );
            
            // Format date range label
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d");
            DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
            if (fromDate.getYear() == toDate.getYear()) {
                if (fromDate.equals(toDate)) {
                    dateRangeLabel = fromDate.format(yearFormatter);
                } else {
                    dateRangeLabel = fromDate.format(formatter) + " - " + toDate.format(yearFormatter);
                }
            } else {
                dateRangeLabel = fromDate.format(yearFormatter) + " - " + toDate.format(yearFormatter);
            }
        } else {
            // Get all orders
            orders = sellerNetworkService.scopeOrders(
                    orderRepository.findAllByOrderByOrderDateDesc(),
                    currentUser
            );
            dateRangeLabel = "All Time";
        }
        
        // Calculate metrics
        long totalOrders = orders.size();
        long pendingOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.pending)
                .count();
        long deliveredOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.delivered)
                .count();
        
        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidEarnings = BigDecimal.ZERO;
        BigDecimal partialCollected = BigDecimal.ZERO;
        BigDecimal balanceDue = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.canceled) {
                continue;
            }
            BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            BigDecimal billed = order.getFinalBillAmount();
            if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                paidEarnings = paidEarnings.add(billed != null ? billed : orderTotal);
            } else if (order.getPaymentStatus() == Order.PaymentStatus.PARTIALLY_PAID) {
                BigDecimal received = billed != null ? billed : BigDecimal.ZERO;
                if (received.compareTo(orderTotal) > 0) {
                    received = orderTotal;
                }
                if (received.compareTo(BigDecimal.ZERO) < 0) {
                    received = BigDecimal.ZERO;
                }
                partialCollected = partialCollected.add(received);
                BigDecimal remaining = orderTotal.subtract(received);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    balanceDue = balanceDue.add(remaining);
                }
            } else {
                balanceDue = balanceDue.add(orderTotal);
            }
        }
        
        long productsCount = countProductsForUser(currentUser);
        
        // Calculate today's orders (only when showing all-time stats)
        long todayOrders = 0;
        if (fromDate == null && toDate == null) {
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            List<Order> todayOrdersList = sellerNetworkService.scopeOrders(
                    orderRepository.findByOrderDateAfterOrderByOrderDateDesc(startOfDay),
                    currentUser
            );
            // Filter to only today (not future dates)
            LocalDateTime endOfDay = LocalDateTime.now();
            todayOrders = todayOrdersList.stream()
                    .filter(o -> o.getOrderDate().isBefore(endOfDay) || o.getOrderDate().isEqual(endOfDay))
                    .count();
        } else {
            // When filtered, todayOrders equals totalOrders if the range includes today
            LocalDate today = LocalDate.now();
            if ((fromDate != null && !fromDate.isAfter(today)) && 
                (toDate != null && !toDate.isBefore(today))) {
                // Range includes today, so count today's orders in the filtered set
                LocalDate todayLocal = LocalDate.now();
                todayOrders = orders.stream()
                        .filter(o -> {
                            LocalDate orderDate = o.getOrderDate().toLocalDate();
                            return orderDate.equals(todayLocal);
                        })
                        .count();
            }
        }
        
        return new DashboardStatsResponse(
                totalOrders,
                pendingOrders,
                deliveredOrders,
                totalRevenue,
                paidEarnings,
                partialCollected,
                balanceDue,
                productsCount,
                dateRangeLabel,
                todayOrders,
                buildMonthlyRevenue(currentUser, fromDate, toDate),
                buildEarningsGraph(orders, fromDate, toDate)
        );
    }

    private long countProductsForUser(User currentUser) {
        if (currentUser != null && currentUser.getRole() == User.UserRole.seller) {
            Seller seller = sellerNetworkService.findSellerForUser(currentUser);
            if (seller == null) {
                return 0L;
            }
            return menuItemRepository.countBySellerId(seller.getId());
        }
        return menuItemRepository.count();
    }

    private List<MonthlyRevenuePoint> buildMonthlyRevenue(User currentUser, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        LocalDate startMonth = today.minusMonths(5).withDayOfMonth(1);
        LocalDate endMonth = today.withDayOfMonth(1);
        if (fromDate != null && toDate != null && !toDate.isBefore(fromDate)) {
            startMonth = fromDate.withDayOfMonth(1);
            endMonth = toDate.withDayOfMonth(1);
            if (startMonth.plusMonths(11).isBefore(endMonth)) {
                startMonth = endMonth.minusMonths(11);
            }
        }
        LocalDateTime rangeStart = startMonth.atStartOfDay();

        Map<String, BigDecimal> orderRevenue = new HashMap<String, BigDecimal>();
        Map<String, Long> orderCounts = new HashMap<String, Long>();
        List<Order> recentOrders = sellerNetworkService.scopeOrders(
                orderRepository.findByOrderDateAfterOrderByOrderDateDesc(rangeStart),
                currentUser
        );
        for (Order order : recentOrders) {
            if (order.getOrderDate() == null || order.getStatus() == Order.OrderStatus.canceled) {
                continue;
            }
            String key = monthKey(order.getOrderDate().toLocalDate());
            BigDecimal amount = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            BigDecimal current = orderRevenue.get(key);
            orderRevenue.put(key, current == null ? amount : current.add(amount));
            Long count = orderCounts.get(key);
            orderCounts.put(key, count == null ? 1L : count + 1L);
        }

        Map<String, BigDecimal> subscriptionRevenue = new HashMap<String, BigDecimal>();
        List<SellerPayment> payments = sellerPaymentRepository.findByPaidAtBetweenOrderByPaidAtDesc(
                rangeStart, endMonth.withDayOfMonth(endMonth.lengthOfMonth()).atTime(23, 59, 59));
        Long sellerId = null;
        if (currentUser != null && currentUser.getRole() == User.UserRole.seller) {
            Seller seller = sellerNetworkService.findSellerForUser(currentUser);
            sellerId = seller != null ? seller.getId() : -1L;
        }
        for (SellerPayment payment : payments) {
            if (sellerId != null && !sellerId.equals(payment.getSellerId())) {
                continue;
            }
            if (payment.getPaidAt() == null || payment.getStatus() != SellerSubscription.PaymentStatus.SUCCESSFUL) {
                continue;
            }
            String key = monthKey(payment.getPaidAt().toLocalDate());
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            BigDecimal current = subscriptionRevenue.get(key);
            subscriptionRevenue.put(key, current == null ? amount : current.add(amount));
        }

        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MMM");
        List<MonthlyRevenuePoint> points = new ArrayList<MonthlyRevenuePoint>();
        for (int i = 0; !startMonth.plusMonths(i).isAfter(endMonth); i++) {
            LocalDate month = startMonth.plusMonths(i);
            String key = monthKey(month);
            BigDecimal orders = orderRevenue.get(key);
            if (orders == null) {
                orders = BigDecimal.ZERO;
            }
            BigDecimal subscriptions = subscriptionRevenue.get(key);
            if (subscriptions == null) {
                subscriptions = BigDecimal.ZERO;
            }
            Long count = orderCounts.get(key);
            if (count == null) {
                count = 0L;
            }
            points.add(new MonthlyRevenuePoint(
                    month.format(labelFormat),
                    key,
                    orders,
                    subscriptions,
                    orders.add(subscriptions),
                    count
            ));
        }
        return points;
    }

    private List<MonthlyRevenuePoint> buildEarningsGraph(List<Order> orders, LocalDate fromDate, LocalDate toDate) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(5).withDayOfMonth(1);
        boolean daily = false;
        if (fromDate != null && toDate != null && !toDate.isBefore(fromDate)) {
            start = fromDate;
            end = toDate;
            daily = ChronoUnit.DAYS.between(start, end) <= 31;
        }
        DateTimeFormatter labelFormat = daily
                ? DateTimeFormatter.ofPattern("d MMM")
                : DateTimeFormatter.ofPattern("MMM");
        Map<String, BigDecimal> paidMap = new HashMap<String, BigDecimal>();
        Map<String, BigDecimal> partialMap = new HashMap<String, BigDecimal>();
        Map<String, BigDecimal> dueMap = new HashMap<String, BigDecimal>();
        Map<String, Long> counts = new HashMap<String, Long>();
        for (Order order : orders) {
            if (order.getOrderDate() == null || order.getStatus() == Order.OrderStatus.canceled) {
                continue;
            }
            LocalDate orderDay = order.getOrderDate().toLocalDate();
            if (orderDay.isBefore(start) || orderDay.isAfter(end)) {
                continue;
            }
            String key = daily ? orderDay.toString() : monthKey(orderDay);
            BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            BigDecimal billed = order.getFinalBillAmount();
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal partial = BigDecimal.ZERO;
            BigDecimal due = BigDecimal.ZERO;
            if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                paid = billed != null ? billed : orderTotal;
            } else if (order.getPaymentStatus() == Order.PaymentStatus.PARTIALLY_PAID) {
                partial = billed != null ? billed : BigDecimal.ZERO;
                if (partial.compareTo(orderTotal) > 0) {
                    partial = orderTotal;
                }
                if (partial.compareTo(BigDecimal.ZERO) < 0) {
                    partial = BigDecimal.ZERO;
                }
                due = orderTotal.subtract(partial);
                if (due.compareTo(BigDecimal.ZERO) < 0) {
                    due = BigDecimal.ZERO;
                }
            } else {
                due = orderTotal;
            }
            paidMap.put(key, addMoney(paidMap.get(key), paid));
            partialMap.put(key, addMoney(partialMap.get(key), partial));
            dueMap.put(key, addMoney(dueMap.get(key), due));
            Long count = counts.get(key);
            counts.put(key, count == null ? 1L : count + 1L);
        }
        List<MonthlyRevenuePoint> points = new ArrayList<MonthlyRevenuePoint>();
        if (daily) {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                points.add(earningsPoint(day.format(labelFormat), day.toString(), paidMap, partialMap, dueMap, counts));
            }
        } else {
            LocalDate month = start.withDayOfMonth(1);
            LocalDate last = end.withDayOfMonth(1);
            while (!month.isAfter(last)) {
                points.add(earningsPoint(month.format(labelFormat), monthKey(month), paidMap, partialMap, dueMap, counts));
                month = month.plusMonths(1);
            }
        }
        return points;
    }

    private MonthlyRevenuePoint earningsPoint(
            String label,
            String key,
            Map<String, BigDecimal> paidMap,
            Map<String, BigDecimal> partialMap,
            Map<String, BigDecimal> dueMap,
            Map<String, Long> counts) {
        BigDecimal paid = paidMap.get(key);
        BigDecimal partial = partialMap.get(key);
        BigDecimal due = dueMap.get(key);
        if (paid == null) {
            paid = BigDecimal.ZERO;
        }
        if (partial == null) {
            partial = BigDecimal.ZERO;
        }
        if (due == null) {
            due = BigDecimal.ZERO;
        }
        Long count = counts.get(key);
        if (count == null) {
            count = 0L;
        }
        return new MonthlyRevenuePoint(label, key, paid, partial, paid.add(partial).add(due), count);
    }

    private BigDecimal addMoney(BigDecimal current, BigDecimal amount) {
        if (current == null) {
            return amount;
        }
        return current.add(amount);
    }

    private String monthKey(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }
}

