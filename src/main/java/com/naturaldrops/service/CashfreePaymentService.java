package com.naturaldrops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.naturaldrops.config.CashfreeProperties;
import com.naturaldrops.dto.request.RecordSellerPaymentRequest;
import com.naturaldrops.dto.request.RenewSubscriptionRequest;
import com.naturaldrops.entity.CashfreePayment;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.PaymentClaim;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerSubscription;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.CashfreePaymentRepository;
import com.naturaldrops.repository.PaymentClaimRepository;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.SellerSubscriptionRepository;
import com.naturaldrops.service.cashfree.CashfreeApiClient;
import com.naturaldrops.service.cashfree.CashfreeSignatureVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePaymentService {

    private static final List<CashfreePayment.Status> OPEN_STATUSES = Arrays.asList(
            CashfreePayment.Status.CREATED,
            CashfreePayment.Status.PENDING
    );

    private final CashfreeProperties properties;
    private final CashfreeApiClient cashfreeApiClient;
    private final CashfreePaymentRepository cashfreePaymentRepository;
    private final PaymentClaimRepository paymentClaimRepository;
    private final SellerSubscriptionRepository sellerSubscriptionRepository;
    private final SellerRepository sellerRepository;
    private final OrderService orderService;
    private final ShopService shopService;
    private final SellerNetworkService sellerNetworkService;
    private final SellerAdminService sellerAdminService;
    private final SettingsService settingsService;

    /**
     * Creates a Cashfree order for (part of) an unpaid Natural Drops bill.
     * {@code requestedAmount} may be null → full unpaid balance. Otherwise must be &gt; 0 and ≤ balance.
     * Server always caps against live unpaid balance (client cannot overpay).
     */
    @Transactional
    public Map<String, Object> createOrderForBuyer(User buyer, Long naturalDropsOrderId) {
        return createOrderForBuyer(buyer, naturalDropsOrderId, null);
    }

    @Transactional
    public Map<String, Object> createOrderForBuyer(User buyer, Long naturalDropsOrderId, BigDecimal requestedAmount) {
        requireBuyer(buyer);
        if (naturalDropsOrderId == null) {
            throw new BadRequestException("Select the unpaid bill you are paying");
        }

        // Lock the Natural Drops order row to serialize Cashfree create vs UPI claim/confirm
        Order order = orderService.getOrderByIdForUpdate(naturalDropsOrderId);
        if (!buyer.getId().equals(order.getBuyerId())) {
            throw new UnauthorizedException("You can pay only your own order");
        }
        if (order.getStatus() == Order.OrderStatus.canceled) {
            throw new BadRequestException("A canceled order cannot be paid");
        }

        BigDecimal balance = orderService.unpaidBillBalance(order).setScale(2, RoundingMode.HALF_UP);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("This bill has no unpaid balance");
        }

        BigDecimal amount = resolvePayAmount(requestedAmount, balance);

        // Reuse existing payable checks (shop customer + amount vs balance)
        shopService.assertBuyerCanPayOrder(buyer.getId(), naturalDropsOrderId, amount);

        if (paymentClaimRepository.findByOrderIdAndStatus(naturalDropsOrderId, PaymentClaim.Status.PENDING).isPresent()) {
            throw new BadRequestException(
                    "This bill already has a UPI payment waiting for the seller. Confirm or reject it before using Cashfree.");
        }

        Optional<CashfreePayment> existingOpen = cashfreePaymentRepository
                .findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(naturalDropsOrderId, OPEN_STATUSES);
        if (existingOpen.isPresent()
                && existingOpen.get().getAmount().compareTo(amount) == 0
                && existingOpen.get().getPaymentSessionId() != null
                && !existingOpen.get().getPaymentSessionId().trim().isEmpty()) {
            return toCreateResponse(existingOpen.get());
        }

        String cfOrderId = "ND" + naturalDropsOrderId + "T" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        if (cfOrderId.length() > 50) {
            cfOrderId = cfOrderId.substring(0, 50);
        }

        String phone = buyer.getPhone() != null ? buyer.getPhone() : buyer.getUsername();
        String name = buyer.getFullName() != null && !buyer.getFullName().trim().isEmpty()
                ? buyer.getFullName().trim()
                : buyer.getUsername();

        JsonNode cfResponse = cashfreeApiClient.createOrder(
                cfOrderId,
                amount,
                String.valueOf(buyer.getId()),
                phone,
                buyer.getEmail(),
                name);

        String sessionId = text(cfResponse, "payment_session_id");
        String returnedOrderId = text(cfResponse, "order_id");
        if (sessionId == null || sessionId.isEmpty()) {
            throw new BadRequestException("Cashfree did not return a payment session");
        }
        if (returnedOrderId != null && !returnedOrderId.isEmpty()) {
            cfOrderId = returnedOrderId;
        }

        CashfreePayment payment = new CashfreePayment();
        payment.setPurpose(CashfreePayment.Purpose.ORDER_BILL);
        payment.setOrderId(naturalDropsOrderId);
        payment.setBuyerUserId(buyer.getId());
        payment.setSellerUserId(order.getSellerUserId() != null
                ? order.getSellerUserId()
                : sellerNetworkService.sellerUserIdForBuyer(buyer.getId()));
        payment.setCfOrderId(cfOrderId);
        payment.setPaymentSessionId(sessionId);
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setStatus(CashfreePayment.Status.CREATED);
        payment.setCfOrderStatus(text(cfResponse, "order_status"));
        payment.setEnvironment(properties.environmentLabel());
        payment.setPaymentApplied(false);
        payment = cashfreePaymentRepository.save(payment);

        log.info("Cashfree order created: ndOrderId={} cfOrderId={} amount={} env={}",
                naturalDropsOrderId, payment.getCfOrderId(), amount, payment.getEnvironment());
        return toCreateResponse(payment);
    }

    /**
     * Creates a Cashfree order for seller platform subscription (MONTHLY / YEARLY).
     * On PAID, activates or renews automatically with SellerPayment method GATEWAY.
     */
    @Transactional
    public Map<String, Object> createOrderForSellerSubscription(User sellerUser, String planValue) {
        requireSeller(sellerUser);
        Seller seller = sellerNetworkService.findSellerForUser(sellerUser);
        if (seller == null) {
            throw new ResourceNotFoundException("Seller shop not found");
        }
        sellerNetworkService.ensurePendingSubscription(seller);
        String plan = planValue == null ? "MONTHLY" : planValue.trim().toUpperCase();
        if (!"MONTHLY".equals(plan) && !"YEARLY".equals(plan)) {
            throw new BadRequestException("Choose Monthly or Yearly");
        }
        if (paymentClaimRepository.findBySellerIdAndKindAndStatus(
                seller.getId(), PaymentClaim.Kind.SUBSCRIPTION, PaymentClaim.Status.PENDING).isPresent()) {
            throw new BadRequestException(
                    "A subscription payment is already waiting for admin. Finish or cancel it before paying online.");
        }

        BigDecimal amount = subscriptionAmountFor(plan);
        Optional<CashfreePayment> existingOpen = cashfreePaymentRepository
                .findFirstBySellerUserIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                        sellerUser.getId(), CashfreePayment.Purpose.SELLER_SUBSCRIPTION, OPEN_STATUSES);
        if (existingOpen.isPresent()
                && plan.equalsIgnoreCase(existingOpen.get().getSubscriptionPlan())
                && existingOpen.get().getAmount().compareTo(amount) == 0
                && existingOpen.get().getPaymentSessionId() != null
                && !existingOpen.get().getPaymentSessionId().trim().isEmpty()) {
            return toCreateResponse(existingOpen.get());
        }

        String cfOrderId = "NDSUB" + seller.getId() + "T" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        if (cfOrderId.length() > 50) {
            cfOrderId = cfOrderId.substring(0, 50);
        }

        String phone = sellerUser.getPhone() != null ? sellerUser.getPhone()
                : (seller.getMobile() != null ? seller.getMobile() : sellerUser.getUsername());
        String name = seller.getBusinessName() != null && !seller.getBusinessName().trim().isEmpty()
                ? seller.getBusinessName().trim()
                : (sellerUser.getFullName() != null ? sellerUser.getFullName() : sellerUser.getUsername());

        JsonNode cfResponse = cashfreeApiClient.createOrder(
                cfOrderId,
                amount,
                String.valueOf(sellerUser.getId()),
                phone,
                sellerUser.getEmail() != null ? sellerUser.getEmail() : seller.getEmail(),
                name);

        String sessionId = text(cfResponse, "payment_session_id");
        String returnedOrderId = text(cfResponse, "order_id");
        if (sessionId == null || sessionId.isEmpty()) {
            throw new BadRequestException("Cashfree did not return a payment session");
        }
        if (returnedOrderId != null && !returnedOrderId.isEmpty()) {
            cfOrderId = returnedOrderId;
        }

        CashfreePayment payment = new CashfreePayment();
        payment.setPurpose(CashfreePayment.Purpose.SELLER_SUBSCRIPTION);
        payment.setSubscriptionPlan(plan);
        payment.setOrderId(null);
        payment.setBuyerUserId(sellerUser.getId());
        payment.setSellerUserId(sellerUser.getId());
        payment.setCfOrderId(cfOrderId);
        payment.setPaymentSessionId(sessionId);
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setStatus(CashfreePayment.Status.CREATED);
        payment.setCfOrderStatus(text(cfResponse, "order_status"));
        payment.setEnvironment(properties.environmentLabel());
        payment.setPaymentApplied(false);
        payment = cashfreePaymentRepository.save(payment);

        log.info("Cashfree subscription order created: sellerId={} plan={} cfOrderId={} amount={}",
                seller.getId(), plan, payment.getCfOrderId(), amount);
        return toCreateResponse(payment);
    }

    /**
     * Fetches live status from Cashfree and applies payment only when verified SUCCESS/PAID.
     */
    @Transactional
    public Map<String, Object> verifyAndRefreshStatus(User buyer, String cfOrderId) {
        requireBuyer(buyer);
        CashfreePayment payment = requireOwnedPayment(buyer, cfOrderId);

        JsonNode cfOrder = cashfreeApiClient.fetchOrder(payment.getCfOrderId());
        String orderStatus = text(cfOrder, "order_status");
        // Re-lock payment row before mutating / applying
        CashfreePayment locked = cashfreePaymentRepository.findByCfOrderIdForUpdate(payment.getCfOrderId())
                .orElse(payment);
        locked.setCfOrderStatus(orderStatus);
        mapAndSetStatus(locked, orderStatus, null);

        // Official success for fulfillment: order_status == PAID (not a single failed attempt)
        if (isPaidStatus(orderStatus) && !Boolean.TRUE.equals(locked.getPaymentApplied())) {
            applySuccessfulPaymentLocked(locked, null);
        } else {
            cashfreePaymentRepository.save(locked);
        }

        return toStatusResponse(locked);
    }

    @Transactional
    public Map<String, Object> verifyAndRefreshSubscriptionStatus(User sellerUser, String cfOrderId) {
        requireSeller(sellerUser);
        CashfreePayment payment = requireOwnedSubscriptionPayment(sellerUser, cfOrderId);

        JsonNode cfOrder = cashfreeApiClient.fetchOrder(payment.getCfOrderId());
        String orderStatus = text(cfOrder, "order_status");
        CashfreePayment locked = cashfreePaymentRepository.findByCfOrderIdForUpdate(payment.getCfOrderId())
                .orElse(payment);
        locked.setCfOrderStatus(orderStatus);
        mapAndSetStatus(locked, orderStatus, null);

        if (isPaidStatus(orderStatus) && !Boolean.TRUE.equals(locked.getPaymentApplied())) {
            applySuccessfulPaymentLocked(locked, null);
        } else {
            cashfreePaymentRepository.save(locked);
        }

        return toStatusResponse(locked);
    }

    /**
     * Public webhook: verify signature on raw body, then process idempotently.
     */
    @Transactional
    public Map<String, Object> handleWebhook(String signature, String timestamp, String rawBody) {
        properties.requireConfigured();
        if (!CashfreeSignatureVerifier.isValid(signature, timestamp, rawBody, properties.getClientSecret())) {
            throw new UnauthorizedException("Invalid Cashfree webhook signature");
        }

        JsonNode root;
        try {
            root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawBody == null ? "{}" : rawBody);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid webhook payload");
        }

        String type = text(root, "type");
        JsonNode data = root.has("data") ? root.get("data") : root;
        String cfOrderId = extractOrderId(data);
        if (cfOrderId == null || cfOrderId.isEmpty()) {
            log.warn("Cashfree webhook missing order_id type={}", type);
            Map<String, Object> ignored = new LinkedHashMap<String, Object>();
            ignored.put("processed", false);
            ignored.put("reason", "missing_order_id");
            return ignored;
        }

        Optional<CashfreePayment> optional = cashfreePaymentRepository.findByCfOrderIdForUpdate(cfOrderId);
        if (!optional.isPresent()) {
            log.warn("Cashfree webhook for unknown order_id={}", cfOrderId);
            Map<String, Object> ignored = new LinkedHashMap<String, Object>();
            ignored.put("processed", false);
            ignored.put("reason", "unknown_order");
            return ignored;
        }

        CashfreePayment payment = optional.get();
        if (Boolean.TRUE.equals(payment.getPaymentApplied())) {
            Map<String, Object> dup = toStatusResponse(payment);
            dup.put("processed", true);
            dup.put("duplicate", true);
            return dup;
        }

        String paymentStatus = extractPaymentStatus(data);
        String orderStatus = extractOrderStatus(data);
        if (orderStatus != null) {
            payment.setCfOrderStatus(orderStatus);
        }
        String cfPaymentId = extractPaymentId(data);
        if (cfPaymentId != null) {
            payment.setCfPaymentId(cfPaymentId);
        }

        mapAndSetStatus(payment, orderStatus, paymentStatus);

        boolean successEvent = isSuccessWebhook(type, paymentStatus, orderStatus);
        if (successEvent) {
            // Confirm with Cashfree before marking paid — only order_status PAID fulfills
            JsonNode live = cashfreeApiClient.fetchOrder(payment.getCfOrderId());
            String liveStatus = text(live, "order_status");
            payment.setCfOrderStatus(liveStatus);
            if (!isPaidStatus(liveStatus)) {
                // Failed/pending attempt on a still-open Cashfree order — do not mark ND bill paid
                if (!"FAILED".equalsIgnoreCase(paymentStatus)
                        && !"USER_DROPPED".equalsIgnoreCase(paymentStatus)) {
                    payment.setStatus(CashfreePayment.Status.PENDING);
                }
                cashfreePaymentRepository.save(payment);
                Map<String, Object> pending = toStatusResponse(payment);
                pending.put("processed", false);
                pending.put("reason", "not_paid_on_cashfree");
                return pending;
            }
            applySuccessfulPaymentLocked(payment, cfPaymentId);
            Map<String, Object> ok = toStatusResponse(payment);
            ok.put("processed", true);
            ok.put("duplicate", false);
            return ok;
        }

        cashfreePaymentRepository.save(payment);
        Map<String, Object> result = toStatusResponse(payment);
        result.put("processed", false);
        result.put("reason", "non_success_event");
        return result;
    }

    /**
     * Caller must hold a pessimistic lock on {@code locked} (and this method locks the ND order).
     * Applies ledger + order update at most once; uses min(stored amount, current unpaid balance).
     */
    private void applySuccessfulPaymentLocked(CashfreePayment locked, String cfPaymentId) {
        if (Boolean.TRUE.equals(locked.getPaymentApplied())) {
            return;
        }

        if (locked.getPurpose() == CashfreePayment.Purpose.SELLER_SUBSCRIPTION) {
            applySuccessfulSubscriptionPaymentLocked(locked, cfPaymentId);
            return;
        }

        if (locked.getOrderId() == null) {
            throw new BadRequestException("Cashfree bill payment is missing order id");
        }

        Order order = orderService.getOrderByIdForUpdate(locked.getOrderId());
        BigDecimal balance = orderService.unpaidBillBalance(order).setScale(2, RoundingMode.HALF_UP);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            // Already settled (e.g. UPI confirmed first) — mark Cashfree row applied, no second receipt
            markApplied(locked, cfPaymentId);
            supersedePendingUpiClaim(locked);
            return;
        }

        BigDecimal stored = locked.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = stored.min(balance);
        shopService.assertBuyerCanPayOrder(locked.getBuyerUserId(), locked.getOrderId(), amount);

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("orderId", locked.getOrderId());
        body.put("amount", amount.toPlainString());
        body.put("method", "CASHFREE");
        shopService.applyConfirmedBuyerPayment(locked.getBuyerUserId(), null, body);

        markApplied(locked, cfPaymentId);
        supersedePendingUpiClaim(locked);
        log.info("Cashfree payment applied: ndOrderId={} cfOrderId={} amount={}",
                locked.getOrderId(), locked.getCfOrderId(), amount);
    }

    private void applySuccessfulSubscriptionPaymentLocked(CashfreePayment locked, String cfPaymentId) {
        if (Boolean.TRUE.equals(locked.getPaymentApplied())) {
            return;
        }
        Long sellerUserId = locked.getSellerUserId() != null ? locked.getSellerUserId() : locked.getBuyerUserId();
        Seller seller = sellerRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller shop not found for Cashfree subscription payment"));

        String plan = locked.getSubscriptionPlan() == null ? "MONTHLY" : locked.getSubscriptionPlan().trim().toUpperCase();
        BigDecimal amount = locked.getAmount().setScale(2, RoundingMode.HALF_UP);
        String paidAt = LocalDateTime.now().toString().substring(0, 19);
        String note = "Cashfree " + locked.getCfOrderId();

        SellerSubscription subscription = sellerSubscriptionRepository.findBySellerId(seller.getId()).orElse(null);
        boolean firstActivation = subscription == null
                || subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.PENDING
                || seller.getAccountStatus() == Seller.AccountStatus.PENDING;

        if (firstActivation) {
            if (subscription != null) {
                try {
                    subscription.setPlan(SellerSubscription.Plan.valueOf(plan));
                } catch (Exception ignored) {
                    // keep existing plan
                }
                subscription.setAmount(amount);
                sellerSubscriptionRepository.save(subscription);
            }
            RecordSellerPaymentRequest request = new RecordSellerPaymentRequest();
            request.setMethod("GATEWAY");
            request.setGatewayRef(locked.getCfOrderId());
            request.setReceivedAmount(amount.toPlainString());
            request.setPaidAt(paidAt);
            request.setNote(note);
            request.setPlan(plan);
            sellerAdminService.activateWithPayment(seller.getId(), request, "cashfree");
        } else {
            RenewSubscriptionRequest renew = new RenewSubscriptionRequest();
            renew.setPlan(plan);
            renew.setMethod("GATEWAY");
            renew.setGatewayRef(locked.getCfOrderId());
            renew.setPaidAt(paidAt);
            renew.setNote(note);
            sellerAdminService.renewSubscription(seller.getId(), renew, "cashfree");
        }

        markApplied(locked, cfPaymentId);
        supersedePendingSubscriptionClaim(seller.getId(), locked);
        log.info("Cashfree subscription applied: sellerId={} plan={} cfOrderId={} amount={}",
                seller.getId(), plan, locked.getCfOrderId(), amount);
    }

    private void markApplied(CashfreePayment locked, String cfPaymentId) {
        locked.setStatus(CashfreePayment.Status.SUCCESS);
        locked.setPaymentApplied(true);
        locked.setAppliedAt(LocalDateTime.now());
        if (cfPaymentId != null) {
            locked.setCfPaymentId(cfPaymentId);
        }
        cashfreePaymentRepository.save(locked);
    }

    private void supersedePendingUpiClaim(CashfreePayment payment) {
        if (payment.getOrderId() == null) {
            return;
        }
        Optional<PaymentClaim> pending = paymentClaimRepository.findByOrderIdAndStatus(
                payment.getOrderId(), PaymentClaim.Status.PENDING);
        if (!pending.isPresent()) {
            return;
        }
        PaymentClaim claim = pending.get();
        claim.setStatus(PaymentClaim.Status.REJECTED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy("cashfree");
        String note = claim.getNote() == null ? "" : claim.getNote() + " | ";
        claim.setNote(note + "Superseded by Cashfree payment " + payment.getCfOrderId());
        paymentClaimRepository.save(claim);
    }

    private void supersedePendingSubscriptionClaim(Long sellerId, CashfreePayment payment) {
        Optional<PaymentClaim> pending = paymentClaimRepository.findBySellerIdAndKindAndStatus(
                sellerId, PaymentClaim.Kind.SUBSCRIPTION, PaymentClaim.Status.PENDING);
        if (!pending.isPresent()) {
            return;
        }
        PaymentClaim claim = pending.get();
        claim.setStatus(PaymentClaim.Status.REJECTED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy("cashfree");
        String note = claim.getNote() == null ? "" : claim.getNote() + " | ";
        claim.setNote(note + "Superseded by Cashfree subscription " + payment.getCfOrderId());
        paymentClaimRepository.save(claim);
    }

    private CashfreePayment requireOwnedPayment(User buyer, String cfOrderId) {
        if (cfOrderId == null || cfOrderId.trim().isEmpty()) {
            throw new BadRequestException("Cashfree order id is required");
        }
        CashfreePayment payment = cashfreePaymentRepository.findByCfOrderId(cfOrderId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Cashfree payment not found"));
        if (payment.getPurpose() == CashfreePayment.Purpose.SELLER_SUBSCRIPTION) {
            throw new UnauthorizedException("This is a seller subscription payment");
        }
        if (!buyer.getId().equals(payment.getBuyerUserId())) {
            throw new UnauthorizedException("You can view only your own payment");
        }
        return payment;
    }

    private CashfreePayment requireOwnedSubscriptionPayment(User sellerUser, String cfOrderId) {
        if (cfOrderId == null || cfOrderId.trim().isEmpty()) {
            throw new BadRequestException("Cashfree order id is required");
        }
        CashfreePayment payment = cashfreePaymentRepository.findByCfOrderId(cfOrderId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Cashfree payment not found"));
        if (payment.getPurpose() != CashfreePayment.Purpose.SELLER_SUBSCRIPTION) {
            throw new UnauthorizedException("This is not a seller subscription payment");
        }
        Long ownerId = payment.getSellerUserId() != null ? payment.getSellerUserId() : payment.getBuyerUserId();
        if (!sellerUser.getId().equals(ownerId)) {
            throw new UnauthorizedException("You can view only your own subscription payment");
        }
        return payment;
    }

    private void requireBuyer(User buyer) {
        if (buyer == null || buyer.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Only buyers can use Cashfree bill payment");
        }
    }

    private void requireSeller(User seller) {
        if (seller == null || seller.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only sellers can use Cashfree subscription payment");
        }
    }

    private BigDecimal subscriptionAmountFor(String plan) {
        String key = "YEARLY".equals(plan) ? "planYearlyAmount" : "planMonthlyAmount";
        String raw = settingsService.getSetting(key);
        if (raw == null || raw.trim().isEmpty()) {
            return "YEARLY".equals(plan) ? new BigDecimal("5389.20") : new BigDecimal("499.00");
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new BadRequestException("Plan amount is not configured correctly");
        }
    }

    /**
     * Null/blank request → full balance. Otherwise require positive amount not exceeding balance.
     */
    private BigDecimal resolvePayAmount(BigDecimal requestedAmount, BigDecimal balance) {
        if (requestedAmount == null) {
            return balance;
        }
        BigDecimal amount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Enter a valid amount");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BadRequestException(
                    "Amount cannot exceed " + balance.toPlainString() + " unpaid balance");
        }
        return amount;
    }

    private void mapAndSetStatus(CashfreePayment payment, String orderStatus, String paymentStatus) {
        if (isPaidStatus(orderStatus) || "SUCCESS".equalsIgnoreCase(paymentStatus)) {
            payment.setStatus(CashfreePayment.Status.SUCCESS);
            return;
        }
        if ("EXPIRED".equalsIgnoreCase(orderStatus)) {
            payment.setStatus(CashfreePayment.Status.EXPIRED);
            return;
        }
        if ("FAILED".equalsIgnoreCase(paymentStatus) || "FAILED".equalsIgnoreCase(orderStatus)) {
            payment.setStatus(CashfreePayment.Status.FAILED);
            return;
        }
        if ("USER_DROPPED".equalsIgnoreCase(paymentStatus) || "CANCELLED".equalsIgnoreCase(paymentStatus)) {
            payment.setStatus(CashfreePayment.Status.USER_DROPPED);
            return;
        }
        if (payment.getStatus() == CashfreePayment.Status.CREATED
                || payment.getStatus() == CashfreePayment.Status.PENDING) {
            payment.setStatus(CashfreePayment.Status.PENDING);
        }
    }

    private boolean isPaidStatus(String orderStatus) {
        return "PAID".equalsIgnoreCase(orderStatus);
    }

    private boolean isSuccessWebhook(String type, String paymentStatus, String orderStatus) {
        if (type != null) {
            String t = type.toUpperCase();
            if (t.contains("PAYMENT_SUCCESS") || t.contains("ORDER_PAID") || "PAYMENT_SUCCESS_WEBHOOK".equals(t)) {
                return true;
            }
            if (t.contains("FAILED") || t.contains("USER_DROPPED") || t.contains("CANCEL")) {
                return false;
            }
        }
        return "SUCCESS".equalsIgnoreCase(paymentStatus) || isPaidStatus(orderStatus);
    }

    private String extractOrderId(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.has("order") && data.get("order").hasNonNull("order_id")) {
            return data.get("order").get("order_id").asText();
        }
        if (data.hasNonNull("order_id")) {
            return data.get("order_id").asText();
        }
        return null;
    }

    private String extractOrderStatus(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.has("order") && data.get("order").hasNonNull("order_status")) {
            return data.get("order").get("order_status").asText();
        }
        return text(data, "order_status");
    }

    private String extractPaymentStatus(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.has("payment") && data.get("payment").hasNonNull("payment_status")) {
            return data.get("payment").get("payment_status").asText();
        }
        return text(data, "payment_status");
    }

    private String extractPaymentId(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.has("payment") && data.get("payment").hasNonNull("cf_payment_id")) {
            return data.get("payment").get("cf_payment_id").asText();
        }
        return text(data, "cf_payment_id");
    }

    private Map<String, Object> toCreateResponse(CashfreePayment payment) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("orderId", payment.getOrderId());
        map.put("purpose", payment.getPurpose() == null ? CashfreePayment.Purpose.ORDER_BILL.name() : payment.getPurpose().name());
        map.put("plan", payment.getSubscriptionPlan());
        map.put("cfOrderId", payment.getCfOrderId());
        map.put("paymentSessionId", payment.getPaymentSessionId());
        map.put("amount", payment.getAmount());
        map.put("currency", payment.getCurrency());
        map.put("environment", payment.getEnvironment());
        map.put("status", payment.getStatus().name());
        return map;
    }

    private Map<String, Object> toStatusResponse(CashfreePayment payment) {
        Map<String, Object> map = toCreateResponse(payment);
        map.put("cfOrderStatus", payment.getCfOrderStatus());
        map.put("cfPaymentId", payment.getCfPaymentId());
        map.put("paymentApplied", Boolean.TRUE.equals(payment.getPaymentApplied()));
        map.put("appliedAt", payment.getAppliedAt() == null ? null : payment.getAppliedAt().toString());
        return map;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
