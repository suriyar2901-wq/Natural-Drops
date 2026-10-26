package com.naturaldrops.service;

import com.naturaldrops.entity.Order;
import com.naturaldrops.repository.CanEventRepository;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.OrderStatusHistoryRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import com.naturaldrops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class OrderUnpaidBalanceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private MenuService menuService;
    @Mock private SellerNetworkService sellerNetworkService;
    @Mock private ShopCustomerRepository shopCustomerRepository;
    @Mock private CanEventRepository canEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private CanAccountService canAccountService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderStatusHistoryRepository,
                notificationService,
                menuService,
                sellerNetworkService,
                shopCustomerRepository,
                canEventRepository,
                userRepository,
                canAccountService);
    }

    @Test
    void unpaidBillIsFullTotal() {
        Order order = new Order();
        order.setTotal(new BigDecimal("100.00"));
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        assertEquals(0, new BigDecimal("100.00").compareTo(orderService.unpaidBillBalance(order)));
    }

    @Test
    void partiallyPaidUsesFinalBillAsAlreadyPaid() {
        Order order = new Order();
        order.setTotal(new BigDecimal("100.00"));
        order.setPaymentStatus(Order.PaymentStatus.PARTIALLY_PAID);
        order.setFinalBillAmount(new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("60.00").compareTo(orderService.unpaidBillBalance(order)));
    }

    @Test
    void paidHasZeroBalance() {
        Order order = new Order();
        order.setTotal(new BigDecimal("100.00"));
        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setFinalBillAmount(new BigDecimal("100.00"));
        assertEquals(0, BigDecimal.ZERO.compareTo(orderService.unpaidBillBalance(order)));
    }
}
