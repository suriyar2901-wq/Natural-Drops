package com.naturaldrops.service;

import com.naturaldrops.dto.request.RecordSellerPaymentRequest;
import com.naturaldrops.dto.request.RenewSubscriptionRequest;
import com.naturaldrops.dto.response.SellerSubscriptionAccessResponse;
import com.naturaldrops.entity.PaymentClaim;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerSubscription;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.PaymentClaimRepository;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.SellerSubscriptionRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentClaimService {

    private final PaymentClaimRepository claimRepository;
    private final SellerRepository sellerRepository;
    private final SellerSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final SellerAdminService sellerAdminService;
    private final ShopService shopService;
    private final SellerNetworkService sellerNetworkService;
    private final PushNotificationService pushNotificationService;

    public Map<String, String> platformPaymentDetails() {
        Map<String, String> settings = settingsService.getAllSettings();
        Map<String, String> result = new HashMap<String, String>();
        result.put("upiId", settings.get("upiId") == null ? "" : settings.get("upiId"));
        result.put("qrCodeImage", settings.get("qrCodeImage") == null ? "" : settings.get("qrCodeImage"));
        result.put("planMonthlyAmount", settings.get("planMonthlyAmount") == null ? "499.00" : settings.get("planMonthlyAmount"));
        result.put("planYearlyAmount", settings.get("planYearlyAmount") == null ? "5389.20" : settings.get("planYearlyAmount"));
        return result;
    }

    @Transactional
    public Map<String, String> savePlatformPaymentDetails(Map<String, String> body) {
        if (body == null) {
            throw new BadRequestException("Payment details are required");
        }
        String upiId = body.get("upiId") == null ? "" : body.get("upiId").trim();
        String qr = body.get("qrCodeImage") == null ? "" : body.get("qrCodeImage").trim();
        if (upiId.length() > 0 && !upiId.matches("^[\\w.-]+@[\\w.-]+$")) {
            throw new BadRequestException("Enter a valid UPI ID, for example watercan@upi");
        }
        if (qr.length() > 0 && !qr.startsWith("data:image")) {
            throw new BadRequestException("Upload a QR photo");
        }
        Map<String, String> updates = new HashMap<String, String>();
        updates.put("upiId", upiId);
        updates.put("qrCodeImage", qr);
        settingsService.updateSettings(updates);
        return platformPaymentDetails();
    }

    @Transactional
    public PaymentClaim claimSubscription(User sellerUser, Map<String, Object> body) {
        if (sellerUser == null || sellerUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only sellers can claim a subscription payment");
        }
        Seller seller = sellerNetworkService.findSellerForUser(sellerUser);
        if (seller == null) {
            throw new ResourceNotFoundException("Seller shop not found");
        }
        if (claimRepository.findBySellerIdAndKindAndStatus(seller.getId(), PaymentClaim.Kind.SUBSCRIPTION, PaymentClaim.Status.PENDING).isPresent()) {
            throw new BadRequestException("A subscription payment is already waiting for admin confirmation");
        }
        String plan = body == null || body.get("plan") == null ? "MONTHLY" : String.valueOf(body.get("plan")).trim().toUpperCase();
        if (!"MONTHLY".equals(plan) && !"YEARLY".equals(plan)) {
            throw new BadRequestException("Choose Monthly or Yearly");
        }
        BigDecimal amount = amountFor(plan);
        String method = body == null || body.get("method") == null ? "UPI" : String.valueOf(body.get("method")).trim().toUpperCase();
        if (method.isEmpty()) {
            method = "UPI";
        }
        String reference = body == null || body.get("upiReference") == null ? null : String.valueOf(body.get("upiReference")).trim();
        PaymentClaim claim = new PaymentClaim();
        claim.setKind(PaymentClaim.Kind.SUBSCRIPTION);
        claim.setStatus(PaymentClaim.Status.PENDING);
        claim.setClaimantUserId(sellerUser.getId());
        claim.setSellerId(seller.getId());
        claim.setSellerUserId(sellerUser.getId());
        claim.setAmount(amount);
        claim.setMethod(method);
        claim.setPlan(plan);
        claim.setUpiReference(emptyToNull(reference));
        claim.setNote(seller.getBusinessName() + " paid subscription by " + method);
        return claimRepository.save(claim);
    }

    @Transactional
    public PaymentClaim claimOrderBill(User buyer, Map<String, Object> body) {
        if (buyer == null || buyer.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Only buyers can claim a bill payment");
        }
        if (body == null || body.get("orderId") == null) {
            throw new BadRequestException("Select the unpaid bill you are paying");
        }
        Long orderId = toLong(body.get("orderId"));
        if (claimRepository.findByOrderIdAndStatus(orderId, PaymentClaim.Status.PENDING).isPresent()) {
            throw new BadRequestException("This bill already has a payment waiting for the seller");
        }
        BigDecimal amount = parseAmount(body.get("amount"));
        shopService.assertBuyerCanPayOrder(buyer.getId(), orderId, amount);
        String method = body.get("method") == null ? "UPI" : String.valueOf(body.get("method")).trim().toUpperCase();
        if (method.isEmpty()) {
            method = "UPI";
        }
        String reference = body.get("upiReference") == null ? null : String.valueOf(body.get("upiReference")).trim();
        if ("UPI".equals(method) && (reference == null || reference.length() < 6)) {
            throw new BadRequestException("Enter the UPI transaction / UTR number from your payment app");
        }
        Long sellerUserId = sellerNetworkService.sellerUserIdForBuyer(buyer.getId());
        Seller seller = null;
        if (buyer.getLinkedSellerId() != null) {
            seller = sellerNetworkService.findSellerById(buyer.getLinkedSellerId());
        }
        PaymentClaim claim = new PaymentClaim();
        claim.setKind(PaymentClaim.Kind.ORDER_BILL);
        claim.setStatus(PaymentClaim.Status.PENDING);
        claim.setClaimantUserId(buyer.getId());
        claim.setOrderId(orderId);
        claim.setSellerId(seller != null ? seller.getId() : null);
        claim.setSellerUserId(sellerUserId);
        claim.setAmount(amount);
        claim.setMethod(method);
        claim.setUpiReference(emptyToNull(reference));
        String name = buyer.getFullName() != null && !buyer.getFullName().trim().isEmpty()
                ? buyer.getFullName().trim()
                : buyer.getUsername();
        String amountText = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
        claim.setNote(name + " paid ₹" + amountText + " for order #" + orderId
                + (reference != null && reference.length() > 0 ? " · UTR " + reference : ""));
        PaymentClaim saved = claimRepository.save(claim);
        String inboxBody = name + " paid ₹" + amountText
                + " for order #" + orderId + " by " + method
                + (reference != null && reference.length() > 0 ? ". UTR " + reference : "")
                + ". Open Buyer bill payments and confirm after you see it in your UPI app.";
        sellerNetworkService.postInboxMessage(
                sellerUserId,
                buyer.getId(),
                "Bill payment waiting",
                inboxBody
        );
        if (sellerUserId != null) {
            Map<String, Object> pushData = new HashMap<String, Object>();
            pushData.put("type", "ORDER_BILL_CLAIM");
            pushData.put("claimId", saved.getId());
            pushData.put("orderId", orderId);
            pushNotificationService.sendNotificationToUser(
                    sellerUserId,
                    "Bill payment waiting",
                    name + " paid ₹" + amountText + " for order #" + orderId
                            + (reference != null && reference.length() > 0 ? " · UTR " + reference : ""),
                    pushData
            );
        }
        return saved;
    }

    public List<PaymentClaim> pendingSubscriptions() {
        return claimRepository.findByKindAndStatusOrderByCreatedAtDesc(PaymentClaim.Kind.SUBSCRIPTION, PaymentClaim.Status.PENDING);
    }

    public List<PaymentClaim> pendingOrderBillsForSeller(Long sellerUserId) {
        return claimRepository.findBySellerUserIdAndKindAndStatusOrderByCreatedAtDesc(
                sellerUserId, PaymentClaim.Kind.ORDER_BILL, PaymentClaim.Status.PENDING);
    }

    public List<PaymentClaim> myOrderClaims(Long buyerUserId) {
        return claimRepository.findByClaimantUserIdAndKindOrderByCreatedAtDesc(buyerUserId, PaymentClaim.Kind.ORDER_BILL);
    }

    public List<PaymentClaim> mySubscriptionClaims(Long sellerUserId) {
        return claimRepository.findByClaimantUserIdAndKindOrderByCreatedAtDesc(sellerUserId, PaymentClaim.Kind.SUBSCRIPTION);
    }

    @Transactional
    public PaymentClaim confirmSubscription(Long claimId, User admin) {
        PaymentClaim claim = requirePending(claimId, PaymentClaim.Kind.SUBSCRIPTION);
        requireAdmin(admin);
        Seller seller = sellerRepository.findById(claim.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        String note = claim.getUpiReference() != null ? "UPI ref " + claim.getUpiReference() : claim.getNote();
        SellerSubscription subscription = subscriptionRepository.findBySellerId(seller.getId()).orElse(null);
        boolean firstActivation = subscription == null
                || subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.PENDING
                || seller.getAccountStatus() == Seller.AccountStatus.PENDING;
        if (firstActivation) {
            if (subscription != null && claim.getPlan() != null) {
                try {
                    subscription.setPlan(SellerSubscription.Plan.valueOf(claim.getPlan()));
                } catch (Exception ignored) {
                    // keep existing plan
                }
                subscription.setAmount(claim.getAmount());
                subscriptionRepository.save(subscription);
            }
            RecordSellerPaymentRequest request = new RecordSellerPaymentRequest();
            request.setMethod(claim.getMethod());
            request.setReceivedAmount(claim.getAmount().toPlainString());
            request.setPaidAt(LocalDateTime.now().toString().substring(0, 19));
            request.setNote(note);
            request.setPlan(claim.getPlan());
            sellerAdminService.activateWithPayment(seller.getId(), request, admin.getUsername());
        } else {
            RenewSubscriptionRequest renew = new RenewSubscriptionRequest();
            renew.setPlan(claim.getPlan());
            renew.setPaidAt(LocalDateTime.now().toString().substring(0, 19));
            renew.setNote(note);
            sellerAdminService.renewSubscription(seller.getId(), renew, admin.getUsername());
        }
        claim.setStatus(PaymentClaim.Status.CONFIRMED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy(admin.getUsername());
        return claimRepository.save(claim);
    }

    @Transactional
    public PaymentClaim rejectSubscription(Long claimId, User admin, String reason) {
        PaymentClaim claim = requirePending(claimId, PaymentClaim.Kind.SUBSCRIPTION);
        requireAdmin(admin);
        claim.setStatus(PaymentClaim.Status.REJECTED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy(admin.getUsername());
        if (reason != null && reason.trim().length() > 0) {
            claim.setNote((claim.getNote() == null ? "" : claim.getNote() + " | ") + "Rejected: " + reason.trim());
        }
        return claimRepository.save(claim);
    }

    @Transactional
    public PaymentClaim confirmOrderBill(Long claimId, User sellerUser) {
        PaymentClaim claim = requirePending(claimId, PaymentClaim.Kind.ORDER_BILL);
        if (sellerUser == null || sellerUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only the seller can confirm this payment");
        }
        if (claim.getSellerUserId() == null || !claim.getSellerUserId().equals(sellerUser.getId())) {
            throw new UnauthorizedException("This payment belongs to another shop");
        }
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("amount", claim.getAmount().toPlainString());
        body.put("method", claim.getMethod());
        body.put("orderId", claim.getOrderId());
        User buyer = userRepository.findById(claim.getClaimantUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));
        shopService.applyConfirmedBuyerPayment(buyer.getId(), buyer.getPhone(), body);
        claim.setStatus(PaymentClaim.Status.CONFIRMED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy(sellerUser.getUsername());
        return claimRepository.save(claim);
    }

    @Transactional
    public PaymentClaim rejectOrderBill(Long claimId, User sellerUser, String reason) {
        PaymentClaim claim = requirePending(claimId, PaymentClaim.Kind.ORDER_BILL);
        if (sellerUser == null || sellerUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only the seller can reject this payment");
        }
        if (claim.getSellerUserId() == null || !claim.getSellerUserId().equals(sellerUser.getId())) {
            throw new UnauthorizedException("This payment belongs to another shop");
        }
        claim.setStatus(PaymentClaim.Status.REJECTED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setResolvedBy(sellerUser.getUsername());
        if (reason != null && reason.trim().length() > 0) {
            claim.setNote((claim.getNote() == null ? "" : claim.getNote() + " | ") + "Rejected: " + reason.trim());
        }
        return claimRepository.save(claim);
    }

    public SellerSubscriptionAccessResponse subscriptionAccess(User user) {
        return sellerAdminService.getAccessForUser(user);
    }

    private PaymentClaim requirePending(Long claimId, PaymentClaim.Kind kind) {
        PaymentClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment claim not found"));
        if (claim.getKind() != kind) {
            throw new BadRequestException("Wrong payment claim type");
        }
        if (claim.getStatus() != PaymentClaim.Status.PENDING) {
            throw new BadRequestException("This payment claim is already " + claim.getStatus().name().toLowerCase());
        }
        return claim;
    }

    private void requireAdmin(User user) {
        if (user == null || user.getRole() != User.UserRole.admin) {
            throw new UnauthorizedException("Only admin can manage subscription payments");
        }
    }

    private BigDecimal amountFor(String plan) {
        String key = "YEARLY".equals(plan) ? "planYearlyAmount" : "planMonthlyAmount";
        String raw = settingsService.getSetting(key);
        if (raw == null || raw.trim().isEmpty()) {
            return "YEARLY".equals(plan) ? new BigDecimal("5389.20") : new BigDecimal("499.00");
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (Exception ex) {
            throw new BadRequestException("Plan amount is not configured correctly");
        }
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BadRequestException("Enter a payment amount");
        }
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value).trim()).setScale(2, BigDecimal.ROUND_HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Amount must be greater than 0");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Enter a valid amount");
        }
    }

    private Long toLong(Object value) {
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid order id");
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
