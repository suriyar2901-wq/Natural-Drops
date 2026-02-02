package com.naturaldrops.service;

import com.naturaldrops.dto.response.DashboardStatsResponse;
import com.naturaldrops.entity.Order;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    
    /**
     * Get dashboard statistics for a specific date range
     * @param fromDate Start date (inclusive), null for all time
     * @param toDate End date (inclusive), null for all time
     * @return Dashboard statistics
     */
    public DashboardStatsResponse getDashboardStats(LocalDate fromDate, LocalDate toDate) {
        List<Order> orders;
        String dateRangeLabel;
        
        if (fromDate != null && toDate != null) {
            // Filter by date range
            LocalDateTime startDateTime = fromDate.atStartOfDay();
            LocalDateTime endDateTime = toDate.atTime(23, 59, 59);
            orders = orderRepository.findOrdersBetweenDates(startDateTime, endDateTime);
            
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
            orders = orderRepository.findAllByOrderByOrderDateDesc();
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
        
        long productsCount = menuItemRepository.count();
        
        // Calculate today's orders (only when showing all-time stats)
        long todayOrders = 0;
        if (fromDate == null && toDate == null) {
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            List<Order> todayOrdersList = orderRepository.findByOrderDateAfterOrderByOrderDateDesc(startOfDay);
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
                productsCount,
                dateRangeLabel,
                todayOrders
        );
    }
}

