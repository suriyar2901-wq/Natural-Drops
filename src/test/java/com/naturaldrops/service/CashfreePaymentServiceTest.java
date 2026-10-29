package com.naturaldrops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naturaldrops.config.CashfreeProperties;
import com.naturaldrops.entity.CashfreePayment;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.PaymentClaim;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.CashfreePaymentRepository;
import com.naturaldrops.repository.PaymentClaimRepository;
import com.naturaldrops.service.cashfree.CashfreeApiClient;
import com.naturaldrops.service.cashfree.CashfreeSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashfreePaymentServiceTest {

    @Mock private CashfreeProperties properties;
    @Mock private CashfreeApiClient cashfreeApiClient;
    @Mock private CashfreePaymentRepository cashfreePaymentRepository;
    @Mock private PaymentClaimRepository paymentClaimRepository;
    @Mock private OrderService orderService;
    @Mock private ShopService shopService;
    @Mock private SellerNetworkService sellerNetworkService;

    @InjectMocks
    private CashfreePaymentService service;

    private final ObjectMapper mapper = new ObjectMapper();
    private User buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(11L);
        buyer.setRole(User.UserRole.buyer);
        buyer.setUsername("buyer1");
        buyer.setPhone("9876543210");
        buyer.setFullName("Buyer One");

        order = new Order();
        order.setId(50L);
        order.setBuyerId(11L);
        order.setTotal(new BigDecimal("100.00"));
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        order.setStatus(Order.OrderStatus.pending);
        order.setSellerUserId(10L);
    }

    @Test
    void createOrderRejectsNonBuyer() {
        User seller = new User();
        seller.setId(2L);
        seller.setRole(User.UserRole.seller);
        assertThrows(UnauthorizedException.class, () -> service.createOrderForBuyer(seller, 50L));
    }

    @Test
    void createOrderRejectsOtherBuyersOrder() {
        order.setBuyerId(99L);
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        assertThrows(UnauthorizedException.class, () -> service.createOrderForBuyer(buyer, 50L));
    }

    @Test
    void createOrderDefaultsToFullUnpaidBalance() throws Exception {
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("75.50"));
        when(paymentClaimRepository.findByOrderIdAndStatus(50L, PaymentClaim.Status.PENDING))
                .thenReturn(Optional.empty());
        when(cashfreePaymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(eq(50L), anyList()))
                .thenReturn(Optional.empty());
        when(properties.environmentLabel()).thenReturn("SANDBOX");

        ObjectNode cf = mapper.createObjectNode();
        cf.put("order_id", "ND50T1");
        cf.put("payment_session_id", "session_abc");
        cf.put("order_status", "ACTIVE");
        when(cashfreeApiClient.createOrder(anyString(), eq(new BigDecimal("75.50")), anyString(), anyString(), any(), anyString()))
                .thenReturn(cf);
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.createOrderForBuyer(buyer, 50L);

        assertEquals("session_abc", result.get("paymentSessionId"));
        assertEquals(new BigDecimal("75.50"), result.get("amount"));
        verify(shopService).assertBuyerCanPayOrder(11L, 50L, new BigDecimal("75.50"));
        verify(cashfreeApiClient).createOrder(anyString(), eq(new BigDecimal("75.50")), eq("11"), anyString(), any(), anyString());
    }

    @Test
    void createOrderUsesBuyerRequestedAmountWhenWithinBalance() throws Exception {
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("335.00"));
        when(paymentClaimRepository.findByOrderIdAndStatus(50L, PaymentClaim.Status.PENDING))
                .thenReturn(Optional.empty());
        when(cashfreePaymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(eq(50L), anyList()))
                .thenReturn(Optional.empty());
        when(properties.environmentLabel()).thenReturn("SANDBOX");

        ObjectNode cf = mapper.createObjectNode();
        cf.put("order_id", "ND50PARTIALAMT");
        cf.put("payment_session_id", "session_200");
        cf.put("order_status", "ACTIVE");
        when(cashfreeApiClient.createOrder(anyString(), eq(new BigDecimal("200.00")), anyString(), anyString(), any(), anyString()))
                .thenReturn(cf);
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.createOrderForBuyer(buyer, 50L, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("200.00"), result.get("amount"));
        verify(cashfreeApiClient).createOrder(anyString(), eq(new BigDecimal("200.00")), eq("11"), anyString(), any(), anyString());
    }

    @Test
    void createOrderRejectsAmountAboveBalance() {
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("335.00"));
        assertThrows(BadRequestException.class,
                () -> service.createOrderForBuyer(buyer, 50L, new BigDecimal("400.00")));
        verify(cashfreeApiClient, never()).createOrder(anyString(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void createOrderBlocksWhenPendingUpiClaimExists() {
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("100.00"));
        when(paymentClaimRepository.findByOrderIdAndStatus(50L, PaymentClaim.Status.PENDING))
                .thenReturn(Optional.of(new PaymentClaim()));
        assertThrows(BadRequestException.class, () -> service.createOrderForBuyer(buyer, 50L));
        verify(cashfreeApiClient, never()).createOrder(anyString(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void createOrderRejectsZeroBalance() {
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(BigDecimal.ZERO);
        assertThrows(BadRequestException.class, () -> service.createOrderForBuyer(buyer, 50L));
    }

    @Test
    void verifyStatusDoesNotApplyWhenPending() {
        CashfreePayment payment = basePayment();
        when(cashfreePaymentRepository.findByCfOrderId("ND50T1")).thenReturn(Optional.of(payment));
        when(cashfreePaymentRepository.findByCfOrderIdForUpdate("ND50T1")).thenReturn(Optional.of(payment));

        ObjectNode cf = mapper.createObjectNode();
        cf.put("order_status", "ACTIVE");
        when(cashfreeApiClient.fetchOrder("ND50T1")).thenReturn(cf);
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> status = service.verifyAndRefreshStatus(buyer, "ND50T1");
        assertEquals("PENDING", status.get("status").toString());
        assertFalse((Boolean) status.get("paymentApplied"));
        verify(shopService, never()).applyConfirmedBuyerPayment(anyLong(), any(), any());
    }

    @Test
    void verifyStatusAppliesOnceOnPaid() {
        CashfreePayment payment = basePayment();
        when(cashfreePaymentRepository.findByCfOrderId("ND50T1")).thenReturn(Optional.of(payment));
        when(cashfreePaymentRepository.findByCfOrderIdForUpdate("ND50T1")).thenReturn(Optional.of(payment));
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("100.00"));
        when(paymentClaimRepository.findByOrderIdAndStatus(50L, PaymentClaim.Status.PENDING))
                .thenReturn(Optional.empty());

        ObjectNode cf = mapper.createObjectNode();
        cf.put("order_status", "PAID");
        when(cashfreeApiClient.fetchOrder("ND50T1")).thenReturn(cf);
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> status = service.verifyAndRefreshStatus(buyer, "ND50T1");
        assertEquals("SUCCESS", status.get("status").toString());
        assertTrue((Boolean) status.get("paymentApplied"));

        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(shopService, times(1)).applyConfirmedBuyerPayment(eq(11L), any(), bodyCaptor.capture());
        assertEquals("CASHFREE", bodyCaptor.getValue().get("method"));
        assertEquals("100.00", bodyCaptor.getValue().get("amount"));
    }

    @Test
    void verifyStatusRejectsOtherBuyer() {
        CashfreePayment payment = basePayment();
        payment.setBuyerUserId(99L);
        when(cashfreePaymentRepository.findByCfOrderId("ND50T1")).thenReturn(Optional.of(payment));
        assertThrows(UnauthorizedException.class, () -> service.verifyAndRefreshStatus(buyer, "ND50T1"));
        verify(cashfreeApiClient, never()).fetchOrder(anyString());
    }

    @Test
    void webhookDuplicateUsesRowLockAndSkipsApply() throws Exception {
        when(properties.getClientSecret()).thenReturn("secret");
        CashfreePayment payment = basePayment();
        payment.setPaymentApplied(true);
        payment.setStatus(CashfreePayment.Status.SUCCESS);
        when(cashfreePaymentRepository.findByCfOrderIdForUpdate("ND50T1")).thenReturn(Optional.of(payment));

        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\"ND50T1\",\"order_status\":\"PAID\"},\"payment\":{\"payment_status\":\"SUCCESS\",\"cf_payment_id\":\"pay_1\"}}}";
        String ts = "1617695238078";
        String sig = CashfreeSignatureVerifier.computeSignature(ts, body, "secret");

        Map<String, Object> result = service.handleWebhook(sig, ts, body);
        assertTrue((Boolean) result.get("duplicate"));
        verify(shopService, never()).applyConfirmedBuyerPayment(anyLong(), any(), any());
    }

    @Test
    void webhookRejectsInvalidSignature() {
        when(properties.getClientSecret()).thenReturn("secret");
        assertThrows(UnauthorizedException.class,
                () -> service.handleWebhook("bad", "123", "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}"));
    }

    @Test
    void webhookFailedDoesNotMarkPaid() throws Exception {
        when(properties.getClientSecret()).thenReturn("secret");

        CashfreePayment payment = basePayment();
        when(cashfreePaymentRepository.findByCfOrderIdForUpdate("ND50T1")).thenReturn(Optional.of(payment));
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = "{\"type\":\"PAYMENT_FAILED_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\"ND50T1\",\"order_status\":\"ACTIVE\"},\"payment\":{\"payment_status\":\"FAILED\"}}}";
        String ts = "1617695238078";
        String sig = CashfreeSignatureVerifier.computeSignature(ts, body, "secret");

        Map<String, Object> result = service.handleWebhook(sig, ts, body);
        assertFalse((Boolean) result.get("processed"));
        verify(shopService, never()).applyConfirmedBuyerPayment(anyLong(), any(), any());
    }

    @Test
    void createOrderUsesPartialUnpaidBalance() {
        order.setTotal(new BigDecimal("200.00"));
        order.setPaymentStatus(Order.PaymentStatus.PARTIALLY_PAID);
        order.setFinalBillAmount(new BigDecimal("50.00"));
        when(orderService.getOrderByIdForUpdate(50L)).thenReturn(order);
        when(orderService.unpaidBillBalance(order)).thenReturn(new BigDecimal("150.00"));
        when(paymentClaimRepository.findByOrderIdAndStatus(50L, PaymentClaim.Status.PENDING))
                .thenReturn(Optional.empty());
        when(cashfreePaymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(eq(50L), anyList()))
                .thenReturn(Optional.empty());
        when(properties.environmentLabel()).thenReturn("SANDBOX");
        ObjectNode cf = mapper.createObjectNode();
        cf.put("order_id", "ND50PARTIAL");
        cf.put("payment_session_id", "session_p");
        cf.put("order_status", "ACTIVE");
        when(cashfreeApiClient.createOrder(anyString(), eq(new BigDecimal("150.00")), anyString(), anyString(), any(), anyString()))
                .thenReturn(cf);
        when(cashfreePaymentRepository.save(any(CashfreePayment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.createOrderForBuyer(buyer, 50L);
        assertEquals(new BigDecimal("150.00"), result.get("amount"));
    }

    private CashfreePayment basePayment() {
        CashfreePayment payment = new CashfreePayment();
        payment.setId(1L);
        payment.setOrderId(50L);
        payment.setBuyerUserId(11L);
        payment.setSellerUserId(10L);
        payment.setCfOrderId("ND50T1");
        payment.setPaymentSessionId("session_abc");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");
        payment.setStatus(CashfreePayment.Status.CREATED);
        payment.setEnvironment("SANDBOX");
        payment.setPaymentApplied(false);
        return payment;
    }
}
