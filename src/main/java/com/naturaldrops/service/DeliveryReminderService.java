package com.naturaldrops.service;

import com.naturaldrops.entity.Order;
import com.naturaldrops.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryReminderService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 900000, initialDelay = 20000)
    @Transactional
    public void sendDayBeforeReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Order> due = orderRepository.findDueDeliveryReminders(
                tomorrow,
                Arrays.asList(Order.OrderStatus.canceled, Order.OrderStatus.delivered)
        );
        for (Order order : due) {
            try {
                notificationService.sendDeliveryDayBeforeAlert(order.getId());
                log.info("Sent day-before delivery reminder for order {}", order.getId());
            } catch (Exception ex) {
                log.error("Could not send delivery reminder for order {}: {}", order.getId(), ex.getMessage());
            }
        }
    }
}
