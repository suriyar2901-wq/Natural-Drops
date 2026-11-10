package com.naturaldrops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.GlobalExceptionHandler;
import com.naturaldrops.service.OrderPdfExportService;
import com.naturaldrops.service.OrderService;
import com.naturaldrops.service.SellerNetworkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrderControllerCreateAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderPdfExportService orderPdfExportService;

    @MockBean
    private SellerNetworkService sellerNetworkService;

    @Test
    void createOrderWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleBody(5L))))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).createOrder(any(CreateOrderRequest.class));
    }

    @Test
    void buyerCannotCreateOrderForAnotherBuyer() throws Exception {
        User buyer = new User();
        buyer.setId(5L);
        buyer.setRole(User.UserRole.buyer);
        buyer.setUsername("buyer5");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("currentUser", buyer)
                        .content(objectMapper.writeValueAsString(sampleBody(99L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(orderService, never()).createOrder(any(CreateOrderRequest.class));
    }

    @Test
    void buyerCreateForcesOwnBuyerId() throws Exception {
        User buyer = new User();
        buyer.setId(5L);
        buyer.setRole(User.UserRole.buyer);
        buyer.setUsername("buyer5");
        buyer.setFullName("Buyer Five");

        Order created = new Order();
        created.setId(700L);
        created.setBuyerId(5L);
        created.setPaymentStatus(Order.PaymentStatus.UNPAID);
        created.setPaymentTiming(Order.PaymentTiming.PAY_AFTER_DELIVERY);
        created.setTotal(new BigDecimal("50.00"));
        created.setStatus(Order.OrderStatus.pending);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("currentUser", buyer)
                        .content(objectMapper.writeValueAsString(sampleBody(5L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentTiming").value("PAY_AFTER_DELIVERY"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(captor.capture());
        assertEquals(Long.valueOf(5L), captor.getValue().getBuyerId());
    }

    @Test
    void sellerCanCreateOrderForBuyerId() throws Exception {
        User seller = new User();
        seller.setId(2L);
        seller.setRole(User.UserRole.seller);
        seller.setUsername("seller");

        Order created = new Order();
        created.setId(701L);
        created.setBuyerId(88L);
        created.setPaymentStatus(Order.PaymentStatus.UNPAID);
        created.setPaymentTiming(Order.PaymentTiming.PAY_NOW);
        created.setTotal(new BigDecimal("50.00"));
        created.setStatus(Order.OrderStatus.pending);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(created);

        CreateOrderRequest body = sampleBody(88L);
        body.setPaymentTiming("PAY_NOW");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("currentUser", seller)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentTiming").value("PAY_NOW"));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(captor.capture());
        assertEquals(Long.valueOf(88L), captor.getValue().getBuyerId());
        assertEquals("PAY_NOW", captor.getValue().getPaymentTiming());
    }

    private CreateOrderRequest sampleBody(Long buyerId) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(10L);
        item.setItemName("20L Can");
        item.setQuantity(1);
        item.setRate(new BigDecimal("40.00"));
        item.setSubtotal(new BigDecimal("40.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setBuyerId(buyerId);
        request.setBuyerName("Someone");
        request.setTotal(new BigDecimal("40.00"));
        request.setItems(Collections.singletonList(item));
        return request;
    }
}
