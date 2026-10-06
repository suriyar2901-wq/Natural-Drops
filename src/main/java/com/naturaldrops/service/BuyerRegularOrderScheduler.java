package com.naturaldrops.service;

import com.naturaldrops.entity.BuyerRegularPlan;
import com.naturaldrops.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuyerRegularOrderScheduler {

    private final BuyerRegularOrderService buyerRegularOrderService;
    private final NotificationService notificationService;

    public void placeIfTomorrow(Long buyerId) {
        try {
            BuyerRegularPlan plan = buyerRegularOrderService.planWithItems(buyerId);
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            if (!buyerRegularOrderService.dueOn(plan, tomorrow)) {
                return;
            }
            Order order = buyerRegularOrderService.createScheduledOrder(plan, tomorrow);
            buyerRegularOrderService.markCreated(plan.getId(), tomorrow);
            notificationService.sendDeliveryDayBeforeAlert(order.getId());
            log.info("Created regular order {} for buyer {} on {}", order.getId(), buyerId, tomorrow);
        } catch (Exception ex) {
            log.error("Could not create the next regular order for buyer {}: {}", buyerId, ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 45000)
    public void createTomorrowOrders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<BuyerRegularPlan> plans;
        try {
            plans = buyerRegularOrderService.activePlans();
        } catch (Exception ex) {
            log.error("Could not load regular orders: {}", ex.getMessage());
            return;
        }
        for (BuyerRegularPlan plan : plans) {
            if (!buyerRegularOrderService.dueOn(plan, tomorrow)) {
                continue;
            }
            try {
                Order order = buyerRegularOrderService.createScheduledOrder(plan, tomorrow);
                buyerRegularOrderService.markCreated(plan.getId(), tomorrow);
                notificationService.sendDeliveryDayBeforeAlert(order.getId());
                log.info("Created regular order {} for buyer {} on {}", order.getId(), plan.getBuyerId(), tomorrow);
            } catch (Exception ex) {
                log.error("Could not create regular order for buyer {}: {}", plan.getBuyerId(), ex.getMessage());
            }
        }
    }
}
