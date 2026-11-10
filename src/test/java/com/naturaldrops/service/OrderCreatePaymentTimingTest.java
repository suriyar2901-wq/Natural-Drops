package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.Order;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.repository.CanEventRepository;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.OrderStatusHistoryRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import com.naturaldrops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatePaymentTimingTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private MenuService menuService;
    @Mock private SellerNetworkService sellerNetworkService;
    @Mock private ShopCustomerRepository shopCustomerRepository;
    @Mock private CanEventRepository canEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private CanAccountService canAccountService;
    @Mock private PricingService pricingService;

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
                canAccountService,
                pricingService);
    }

    @Test
    void createOrderWithPayNowSetsTimingAndUnpaid() {
        CreateOrderRequest request = baseRequest();
        request.setPaymentTiming("PAY_NOW");
        request.setTotal(new BigDecimal("1.00")); // must be ignored

        MenuItem menu = menu(10L, "20L Can", new BigDecimal("40.00"));
        when(menuService.getMenuItemById(10L)).thenReturn(menu);
        when(sellerNetworkService.sellerUserIdForBuyer(5L)).thenReturn(99L);
        when(pricingService.totalForBuyer(eq(5L), any(BigDecimal.class), isNull(), isNull()))
                .thenReturn(new BigDecimal("80.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(501L);
            return o;
        });

        Order saved = orderService.createOrder(request);

        assertEquals(Order.PaymentTiming.PAY_NOW, saved.getPaymentTiming());
        assertEquals(Order.PaymentStatus.UNPAID, saved.getPaymentStatus());
        assertEquals(0, new BigDecimal("80.00").compareTo(saved.getTotal()));
        assertEquals(0, new BigDecimal("40.00").compareTo(saved.getItems().get(0).getRate()));
        assertEquals(Order.OrderStatus.pending, saved.getStatus());
    }

    @Test
    void createOrderWithPayAfterDelivery() {
        CreateOrderRequest request = baseRequest();
        request.setPaymentTiming("PAY_AFTER_DELIVERY");

        stubHappyPath(new BigDecimal("50.00"));

        Order saved = orderService.createOrder(request);

        assertEquals(Order.PaymentTiming.PAY_AFTER_DELIVERY, saved.getPaymentTiming());
        assertEquals(Order.PaymentStatus.UNPAID, saved.getPaymentStatus());
    }

    @Test
    void createOrderWithoutPaymentTimingDefaultsToPayAfterDelivery() {
        CreateOrderRequest request = baseRequest();
        request.setPaymentTiming(null);

        stubHappyPath(new BigDecimal("50.00"));

        Order saved = orderService.createOrder(request);

        assertEquals(Order.PaymentTiming.PAY_AFTER_DELIVERY, saved.getPaymentTiming());
        assertEquals(Order.PaymentStatus.UNPAID, saved.getPaymentStatus());
    }

    @Test
    void createOrderRejectsUnsupportedPaymentTiming() {
        CreateOrderRequest request = baseRequest();
        request.setPaymentTiming("COD");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.createOrder(request));
        assertEquals("paymentTiming must be PAY_NOW or PAY_AFTER_DELIVERY", ex.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderIgnoresClientTotalAndUsesServerMenuRate() {
        CreateOrderRequest request = baseRequest();
        request.setPaymentTiming("PAY_NOW");
        request.setTotal(new BigDecimal("9999.00"));
        request.getItems().get(0).setRate(new BigDecimal("1.00")); // client underprice
        request.getItems().get(0).setSubtotal(new BigDecimal("2.00"));

        MenuItem menu = menu(10L, "20L Can", new BigDecimal("45.00"));
        when(menuService.getMenuItemById(10L)).thenReturn(menu);
        when(sellerNetworkService.sellerUserIdForBuyer(5L)).thenReturn(99L);
        when(pricingService.totalForBuyer(eq(5L), any(BigDecimal.class), isNull(), isNull()))
                .thenAnswer(invocation -> {
                    BigDecimal subtotal = invocation.getArgument(1);
                    assertEquals(0, new BigDecimal("90.00").compareTo(subtotal));
                    return new BigDecimal("95.00");
                });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(502L);
            return o;
        });

        Order saved = orderService.createOrder(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("45.00").compareTo(captor.getValue().getItems().get(0).getRate()));
        assertEquals(0, new BigDecimal("95.00").compareTo(saved.getTotal()));
        assertEquals(Order.PaymentStatus.UNPAID, saved.getPaymentStatus());
    }

    @Test
    void historicalNullPaymentTimingRemainsReadable() {
        Order historical = new Order();
        historical.setId(1L);
        historical.setTotal(new BigDecimal("100.00"));
        historical.setPaymentStatus(null);
        historical.setPaymentTiming(null);

        assertNull(historical.getPaymentTiming());
        assertEquals(0, new BigDecimal("100.00").compareTo(orderService.unpaidBillBalance(historical)));
    }

    @Test
    void resolvePaymentTimingBlankDefaults() {
        assertEquals(Order.PaymentTiming.PAY_AFTER_DELIVERY, orderService.resolvePaymentTiming(null));
        assertEquals(Order.PaymentTiming.PAY_AFTER_DELIVERY, orderService.resolvePaymentTiming("  "));
        assertEquals(Order.PaymentTiming.PAY_NOW, orderService.resolvePaymentTiming("pay_now"));
    }

    private void stubHappyPath(BigDecimal pricedTotal) {
        MenuItem menu = menu(10L, "20L Can", new BigDecimal("25.00"));
        when(menuService.getMenuItemById(10L)).thenReturn(menu);
        when(sellerNetworkService.sellerUserIdForBuyer(5L)).thenReturn(99L);
        when(pricingService.totalForBuyer(eq(5L), any(BigDecimal.class), isNull(), isNull()))
                .thenReturn(pricedTotal);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(500L);
            return o;
        });
    }

    private CreateOrderRequest baseRequest() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(10L);
        item.setItemName("20L Can");
        item.setQuantity(2);
        item.setRate(new BigDecimal("1.00"));
        item.setCartQuantity(2);
        item.setSubtotal(new BigDecimal("2.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setBuyerId(5L);
        request.setBuyerName("Buyer");
        request.setBuyerPhone("9876543210");
        request.setBuyerAddress("Addr");
        request.setDeliveryAddress("Addr");
        request.setTotal(new BigDecimal("100.00"));
        request.setItems(Collections.singletonList(item));
        return request;
    }

    private MenuItem menu(Long id, String name, BigDecimal rate) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(id);
        menuItem.setName(name);
        menuItem.setRate(rate);
        return menuItem;
    }
}
