package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateSellerRequest;
import com.naturaldrops.dto.request.DeactivateSellerRequest;
import com.naturaldrops.dto.request.RecordSellerPaymentRequest;
import com.naturaldrops.dto.request.RenewSubscriptionRequest;
import com.naturaldrops.dto.request.UpdateSellerRequest;
import com.naturaldrops.dto.response.PlatformDashboardResponse;
import com.naturaldrops.dto.response.SellerAdminResponse;
import com.naturaldrops.dto.response.SellerPaymentResponse;
import com.naturaldrops.dto.response.SellerSubscriptionAccessResponse;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerPayment;
import com.naturaldrops.entity.SellerSubscription;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.SellerPaymentRepository;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.SellerSubscriptionRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SellerAdminService {

    public static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("499.00");
    public static final BigDecimal YEARLY_AMOUNT = new BigDecimal("5389.20");

    private static final Pattern MOBILE = Pattern.compile("^[0-9]{10}$");
    private static final Pattern PINCODE = Pattern.compile("^[0-9]{6}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_TIME_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final SellerRepository sellerRepository;
    private final SellerSubscriptionRepository subscriptionRepository;
    private final SellerPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SellerNetworkService sellerNetworkService;

    @Transactional(readOnly = true)
    public List<SellerAdminResponse> listSellers() {
        List<Seller> sellers = sellerRepository.findAll();
        List<SellerAdminResponse> result = new ArrayList<SellerAdminResponse>();
        for (Seller seller : sellers) {
            result.add(toSellerResponse(seller, false));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SellerAdminResponse getSeller(Long id) {
        Seller seller = findSeller(id);
        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse createSeller(CreateSellerRequest request, String createdBy) {
        validateCreate(request);
        Seller seller = new Seller();
        seller.setSellerCode(nextSellerCode());
        seller.setOwnerName(request.getOwnerName().trim());
        seller.setMobile(request.getMobile().trim());
        seller.setAlternateMobile(emptyToNull(request.getAlternateMobile()));
        seller.setEmail(emptyToNull(request.getEmail()));
        seller.setBusinessName(request.getBusinessName().trim());
        seller.setBusinessAddress(request.getBusinessAddress().trim());
        seller.setArea(request.getArea().trim());
        seller.setCity(request.getCity().trim());
        seller.setPincode(request.getPincode().trim());
        seller.setAccountStatus(Seller.AccountStatus.PENDING);
        seller.setCreatedBy(createdBy);
        sellerNetworkService.assignCompanyCode(seller);
        seller = sellerRepository.save(seller);

        SellerSubscription.Plan plan = parsePlan(request.getPlan());
        SellerSubscription subscription = new SellerSubscription();
        subscription.setSellerId(seller.getId());
        subscription.setPlan(plan);
        subscription.setAmount(amountFor(plan));
        subscription.setPaymentStatus(SellerSubscription.PaymentStatus.PENDING);
        subscriptionRepository.save(subscription);

        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse updateSeller(Long id, UpdateSellerRequest request) {
        Seller seller = findSeller(id);
        if (request.getOwnerName() == null || request.getOwnerName().trim().length() < 2) {
            throw new BadRequestException("Owner name is required");
        }
        if (request.getMobile() == null || !MOBILE.matcher(request.getMobile().trim()).matches()) {
            throw new BadRequestException("Mobile must be 10 digits");
        }
        if (request.getBusinessName() == null || request.getBusinessName().trim().length() < 2) {
            throw new BadRequestException("Business name is required");
        }
        if (request.getBusinessAddress() == null || request.getBusinessAddress().trim().length() < 4) {
            throw new BadRequestException("Business address is required");
        }
        if (request.getArea() == null || request.getArea().trim().length() < 2) {
            throw new BadRequestException("Area is required");
        }
        if (request.getCity() == null || request.getCity().trim().length() < 2) {
            throw new BadRequestException("City is required");
        }
        if (request.getPincode() == null || !PINCODE.matcher(request.getPincode().trim()).matches()) {
            throw new BadRequestException("Pincode must be 6 digits");
        }
        if (request.getEmail() != null && request.getEmail().trim().length() > 0
                && !EMAIL.matcher(request.getEmail().trim()).matches()) {
            throw new BadRequestException("Invalid email format");
        }

        seller.setOwnerName(request.getOwnerName().trim());
        seller.setMobile(request.getMobile().trim());
        seller.setAlternateMobile(emptyToNull(request.getAlternateMobile()));
        seller.setEmail(emptyToNull(request.getEmail()));
        seller.setBusinessName(request.getBusinessName().trim());
        seller.setBusinessAddress(request.getBusinessAddress().trim());
        seller.setArea(request.getArea().trim());
        seller.setCity(request.getCity().trim());
        seller.setPincode(request.getPincode().trim());
        if (request.getChangeNote() != null && request.getChangeNote().trim().length() > 0) {
            seller.setAdminNote(request.getChangeNote().trim());
        }
        sellerRepository.save(seller);
        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse deactivateSeller(Long id, DeactivateSellerRequest request) {
        Seller seller = findSeller(id);
        if (request == null || request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BadRequestException("Deactivation reason is required");
        }
        seller.setAccountStatus(Seller.AccountStatus.DEACTIVATED);
        seller.setDeactivationReason(request.getReason().trim());
        seller.setAdminNote(emptyToNull(request.getAdminNote()));
        sellerRepository.save(seller);
        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse reactivateSeller(Long id, String adminNote) {
        Seller seller = findSeller(id);
        seller.setAccountStatus(Seller.AccountStatus.ACTIVE);
        seller.setDeactivationReason(null);
        if (adminNote != null && adminNote.trim().length() > 0) {
            seller.setAdminNote(adminNote.trim());
        }
        sellerRepository.save(seller);
        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse activateWithPayment(Long sellerId, RecordSellerPaymentRequest request, String createdBy) {
        Seller seller = findSeller(sellerId);
        SellerSubscription subscription = subscriptionRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for seller"));

        SellerPayment.Method method = parseMethod(request.getMethod());
        BigDecimal received = parseAmount(request.getReceivedAmount());
        if (received.compareTo(subscription.getAmount()) != 0) {
            throw new BadRequestException("Received amount must exactly match due amount " + subscription.getAmount());
        }

        LocalDateTime paidAt = parseDateTime(request.getPaidAt());
        SellerPayment payment = createPayment(seller, subscription, method, received,
                SellerSubscription.PaymentStatus.SUCCESSFUL, paidAt, request.getNote(), createdBy);
        paymentRepository.save(payment);

        LocalDate start = paidAt.toLocalDate();
        subscription.setStartDate(start);
        subscription.setExpiryDate(extendFrom(start, subscription.getPlan()));
        subscription.setPaymentStatus(SellerSubscription.PaymentStatus.SUCCESSFUL);
        subscriptionRepository.save(subscription);

        if (seller.getAccountStatus() != Seller.AccountStatus.DEACTIVATED) {
            seller.setAccountStatus(Seller.AccountStatus.ACTIVE);
            sellerRepository.save(seller);
        }
        return toSellerResponse(seller, true);
    }

    @Transactional
    public SellerAdminResponse renewSubscription(Long sellerId, RenewSubscriptionRequest request, String createdBy) {
        Seller seller = findSeller(sellerId);
        SellerSubscription subscription = subscriptionRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for seller"));

        SellerSubscription.Plan plan = request.getPlan() != null && request.getPlan().trim().length() > 0
                ? parsePlan(request.getPlan())
                : subscription.getPlan();
        BigDecimal amount = amountFor(plan);
        LocalDateTime paidAt = parseDateTime(request.getPaidAt());

        SellerPayment payment = createPayment(seller, subscription, SellerPayment.Method.CASH, amount,
                SellerSubscription.PaymentStatus.SUCCESSFUL, paidAt, request.getNote(), createdBy);
        paymentRepository.save(payment);

        LocalDate today = paidAt.toLocalDate();
        LocalDate base = subscription.getExpiryDate() != null && !subscription.getExpiryDate().isBefore(today)
                ? subscription.getExpiryDate()
                : today;
        if (subscription.getStartDate() == null) {
            subscription.setStartDate(today);
        }
        subscription.setPlan(plan);
        subscription.setAmount(amount);
        subscription.setExpiryDate(extendFrom(base, plan));
        subscription.setPaymentStatus(SellerSubscription.PaymentStatus.SUCCESSFUL);
        subscriptionRepository.save(subscription);

        return toSellerResponse(seller, true);
    }

    @Transactional(readOnly = true)
    public SellerSubscriptionAccessResponse getAccessForUser(User user) {
        Seller seller = resolveSellerForUser(user, false);
        SellerSubscription subscription = seller == null
                ? null
                : subscriptionRepository.findBySellerId(seller.getId()).orElse(null);
        return toAccessResponse(seller, subscription);
    }

    @Transactional
    public SellerSubscriptionAccessResponse subscribeForUser(User user, String planValue, String methodValue) {
        if (user.getPhone() == null || !MOBILE.matcher(user.getPhone().trim()).matches()) {
            throw new BadRequestException("Add a 10-digit mobile number in Profile before subscribing");
        }
        Seller seller = resolveSellerForUser(user, true);
        seller.setUserId(user.getId());
        if (seller.getAccountStatus() != Seller.AccountStatus.DEACTIVATED) {
            seller.setAccountStatus(Seller.AccountStatus.ACTIVE);
        }
        sellerRepository.save(seller);

        SellerSubscription.Plan plan = parsePlan(planValue);
        BigDecimal amount = amountFor(plan);
        SellerPayment.Method method = parseMethod(methodValue == null ? "UPI" : methodValue);

        SellerSubscription subscription = subscriptionRepository.findBySellerId(seller.getId()).orElse(null);
        if (subscription == null) {
            subscription = new SellerSubscription();
            subscription.setSellerId(seller.getId());
        }
        LocalDateTime paidAt = LocalDateTime.now();
        LocalDate today = paidAt.toLocalDate();
        LocalDate base = subscription.getExpiryDate() != null && !subscription.getExpiryDate().isBefore(today)
                ? subscription.getExpiryDate()
                : today;
        if (subscription.getStartDate() == null) {
            subscription.setStartDate(today);
        }
        subscription.setPlan(plan);
        subscription.setAmount(amount);
        subscription.setExpiryDate(extendFrom(base, plan));
        subscription.setPaymentStatus(SellerSubscription.PaymentStatus.SUCCESSFUL);
        subscription = subscriptionRepository.save(subscription);

        SellerPayment payment = createPayment(seller, subscription, method, amount,
                SellerSubscription.PaymentStatus.SUCCESSFUL, paidAt, "Seller self-subscribe", user.getUsername());
        paymentRepository.save(payment);
        return toAccessResponse(seller, subscription);
    }

    @Transactional(readOnly = true)
    public List<SellerAdminResponse> listSubscriptions() {
        return listSellers();
    }

    @Transactional(readOnly = true)
    public List<SellerPaymentResponse> listPayments() {
        List<SellerPayment> payments = paymentRepository.findAllByOrderByPaidAtDesc();
        Map<Long, Seller> sellers = indexSellers();
        List<SellerPaymentResponse> result = new ArrayList<SellerPaymentResponse>();
        for (SellerPayment payment : payments) {
            result.add(toPaymentResponse(payment, sellers.get(payment.getSellerId())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SellerPaymentResponse getPayment(Long id) {
        SellerPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Seller seller = sellerRepository.findById(payment.getSellerId()).orElse(null);
        return toPaymentResponse(payment, seller);
    }

    @Transactional(readOnly = true)
    public PlatformDashboardResponse getDashboard(String period) {
        String safePeriod = period == null || period.trim().isEmpty() ? "this_month" : period.trim();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = monthStart.minusDays(1);

        List<Seller> sellers = sellerRepository.findAll();
        Map<Long, SellerSubscription> subscriptions = indexSubscriptions();
        List<SellerPayment> allPayments = paymentRepository.findAll();

        PlatformDashboardResponse response = new PlatformDashboardResponse();
        response.setPeriod(safePeriod);
        response.setTotalSellers(sellers.size());

        long activeSubs = 0;
        long expiring = 0;
        long expired = 0;
        long pending = 0;
        long deactivated = 0;
        long newSellers = 0;
        for (Seller seller : sellers) {
            if (seller.getAccountStatus() == Seller.AccountStatus.DEACTIVATED) {
                deactivated++;
            }
            if (seller.getCreatedAt() != null && !seller.getCreatedAt().toLocalDate().isBefore(monthStart)) {
                newSellers++;
            }
            SellerSubscription subscription = subscriptions.get(seller.getId());
            String status = computeSubscriptionStatus(subscription);
            if ("Active".equals(status)) {
                activeSubs++;
            } else if ("Expiring Soon".equals(status)) {
                expiring++;
            } else if ("Expired".equals(status)) {
                expired++;
            } else {
                pending++;
            }
        }

        response.setActiveSubscriptions(activeSubs);
        response.setActiveSubscribers(activeSubs);
        response.setExpiringSoon(expiring);
        response.setExpired(expired);
        response.setPaymentPending(pending);
        response.setDeactivatedAccounts(deactivated);
        response.setNewSellers(newSellers);
        response.setActiveRate(percent(activeSubs, sellers.size()));

        BigDecimal revenueMonth = BigDecimal.ZERO;
        BigDecimal revenueToday = BigDecimal.ZERO;
        BigDecimal revenueLast = BigDecimal.ZERO;
        BigDecimal yetToReceive = BigDecimal.ZERO;
        long renewed = 0;
        for (SellerPayment payment : allPayments) {
            if (payment.getStatus() == SellerSubscription.PaymentStatus.SUCCESSFUL && payment.getPaidAt() != null) {
                LocalDate paidDate = payment.getPaidAt().toLocalDate();
                if (!paidDate.isBefore(monthStart) && !paidDate.isAfter(today)) {
                    revenueMonth = revenueMonth.add(payment.getAmount());
                    renewed++;
                }
                if (paidDate.equals(today)) {
                    revenueToday = revenueToday.add(payment.getAmount());
                }
                if (!paidDate.isBefore(lastMonthStart) && !paidDate.isAfter(lastMonthEnd)) {
                    revenueLast = revenueLast.add(payment.getAmount());
                }
            }
            if (payment.getStatus() == SellerSubscription.PaymentStatus.PENDING) {
                yetToReceive = yetToReceive.add(payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO);
            }
        }
        for (SellerSubscription subscription : subscriptions.values()) {
            if (subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.PENDING
                    && subscription.getAmount() != null) {
                yetToReceive = yetToReceive.add(subscription.getAmount());
            }
        }

        response.setRevenueThisMonth(revenueMonth);
        response.setRevenueToday(revenueToday);
        response.setRevenueLastMonth(revenueLast);
        response.setYetToReceive(yetToReceive);
        response.setExpectedRevenue(revenueMonth.add(yetToReceive));
        response.setRenewed(renewed);
        response.setNotRenewed(expired);
        response.setRenewalRate(percent(renewed, renewed + expired));

        List<User> buyers = userRepository.findByRole(User.UserRole.buyer);
        response.setTotalBuyers(buyers.size());
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        response.setOrdersToday(orderRepository.findOrdersBetweenDates(startOfDay, endOfDay).size());
        response.setOrdersThisMonth(orderRepository.findOrdersBetweenDates(monthStart.atStartOfDay(), endOfDay).size());
        response.setActiveSellersToday(activeSubs);

        List<String> attention = new ArrayList<String>();
        if (expiring > 0) {
            attention.add(expiring + " subscription(s) expiring within 7 days");
        }
        if (pending > 0) {
            attention.add(pending + " seller(s) have payment pending");
        }
        if (expired > 0) {
            attention.add(expired + " subscription(s) expired");
        }
        response.setAttentionItems(attention);

        List<SellerPaymentResponse> recent = new ArrayList<SellerPaymentResponse>();
        List<SellerPayment> ordered = paymentRepository.findAllByOrderByPaidAtDesc();
        Map<Long, Seller> sellerIndex = indexSellers();
        int limit = Math.min(5, ordered.size());
        for (int i = 0; i < limit; i++) {
            SellerPayment payment = ordered.get(i);
            recent.add(toPaymentResponse(payment, sellerIndex.get(payment.getSellerId())));
        }
        response.setRecentPayments(recent);
        return response;
    }

    private void validateCreate(CreateSellerRequest request) {
        if (request == null) {
            throw new BadRequestException("Seller details are required");
        }
        if (request.getOwnerName() == null || request.getOwnerName().trim().length() < 2) {
            throw new BadRequestException("Owner name is required");
        }
        if (request.getMobile() == null || !MOBILE.matcher(request.getMobile().trim()).matches()) {
            throw new BadRequestException("Mobile must be 10 digits");
        }
        if (sellerRepository.existsByMobile(request.getMobile().trim())) {
            throw new BadRequestException("A seller with this mobile already exists");
        }
        if (request.getEmail() != null && request.getEmail().trim().length() > 0
                && !EMAIL.matcher(request.getEmail().trim()).matches()) {
            throw new BadRequestException("Invalid email format");
        }
        if (request.getBusinessName() == null || request.getBusinessName().trim().length() < 2) {
            throw new BadRequestException("Business name is required");
        }
        if (request.getBusinessAddress() == null || request.getBusinessAddress().trim().length() < 4) {
            throw new BadRequestException("Business address is required");
        }
        if (request.getArea() == null || request.getArea().trim().length() < 2) {
            throw new BadRequestException("Area is required");
        }
        if (request.getCity() == null || request.getCity().trim().length() < 2) {
            throw new BadRequestException("City is required");
        }
        if (request.getPincode() == null || !PINCODE.matcher(request.getPincode().trim()).matches()) {
            throw new BadRequestException("Pincode must be 6 digits");
        }
        parsePlan(request.getPlan());
    }

    private Seller findSeller(Long id) {
        return sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
    }

    private String nextSellerCode() {
        long count = sellerRepository.count() + 1;
        String code = String.format("S-%04d", count);
        while (sellerRepository.findBySellerCode(code).isPresent()) {
            count++;
            code = String.format("S-%04d", count);
        }
        return code;
    }

    private SellerSubscription.Plan parsePlan(String plan) {
        if (plan == null || plan.trim().isEmpty()) {
            return SellerSubscription.Plan.MONTHLY;
        }
        try {
            return SellerSubscription.Plan.valueOf(plan.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Plan must be MONTHLY or YEARLY");
        }
    }

    private SellerPayment.Method parseMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            throw new BadRequestException("Payment method is required");
        }
        String normalized = method.trim().toUpperCase().replace(" ", "_");
        if ("COMPANY_QR".equals(normalized) || "QR".equals(normalized) || "COMPANY_QR_/_UPI".equals(normalized)) {
            return SellerPayment.Method.UPI;
        }
        try {
            return SellerPayment.Method.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Payment method must be CASH, UPI or GATEWAY");
        }
    }

    private BigDecimal amountFor(SellerSubscription.Plan plan) {
        return plan == SellerSubscription.Plan.YEARLY ? YEARLY_AMOUNT : MONTHLY_AMOUNT;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("Received amount is required");
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (Exception ex) {
            throw new BadRequestException("Received amount is invalid");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 16) {
                return LocalDateTime.parse(trimmed, DATE_TIME);
            }
            if (trimmed.length() == 19) {
                return LocalDateTime.parse(trimmed, DATE_TIME_SEC);
            }
            return LocalDateTime.parse(trimmed);
        } catch (Exception ex) {
            throw new BadRequestException("Payment date/time is invalid");
        }
    }

    private LocalDate extendFrom(LocalDate base, SellerSubscription.Plan plan) {
        if (plan == SellerSubscription.Plan.YEARLY) {
            return base.plusYears(1);
        }
        return base.plusMonths(1);
    }

    private SellerPayment createPayment(Seller seller, SellerSubscription subscription, SellerPayment.Method method,
                                        BigDecimal amount, SellerSubscription.PaymentStatus status,
                                        LocalDateTime paidAt, String note, String createdBy) {
        SellerPayment payment = new SellerPayment();
        payment.setTransactionCode(nextTransactionCode());
        payment.setSellerId(seller.getId());
        payment.setSubscriptionId(subscription.getId());
        payment.setPlan(subscription.getPlan());
        payment.setAmount(amount);
        payment.setPlanPriceSnapshot(subscription.getAmount());
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setNote(emptyToNull(note));
        payment.setPaidAt(paidAt);
        payment.setCreatedBy(createdBy);
        return payment;
    }

    private String nextTransactionCode() {
        return "TXN-" + String.format("%05d", paymentRepository.count() + 1) + "-" + (System.currentTimeMillis() % 10000);
    }

    private SellerAdminResponse toSellerResponse(Seller seller, boolean includePayments) {
        SellerAdminResponse response = new SellerAdminResponse();
        if (seller.getCompanyCode() == null || seller.getCompanyCode().trim().isEmpty()) {
            sellerNetworkService.assignCompanyCode(seller);
            sellerRepository.save(seller);
        }
        response.setId(seller.getId());
        response.setSellerCode(seller.getSellerCode());
        response.setCompanyCode(seller.getCompanyCode());
        response.setOwnerName(seller.getOwnerName());
        response.setMobile(seller.getMobile());
        response.setAlternateMobile(seller.getAlternateMobile());
        response.setEmail(seller.getEmail());
        response.setBusinessName(seller.getBusinessName());
        response.setBusinessAddress(seller.getBusinessAddress());
        response.setArea(seller.getArea());
        response.setCity(seller.getCity());
        response.setPincode(seller.getPincode());
        response.setAccountStatus(seller.getAccountStatus().name());
        response.setDeactivationReason(seller.getDeactivationReason());
        response.setAdminNote(seller.getAdminNote());
        response.setCreatedAt(seller.getCreatedAt());
        response.setCreatedBy(seller.getCreatedBy());

        SellerSubscription subscription = subscriptionRepository.findBySellerId(seller.getId()).orElse(null);
        if (subscription != null) {
            response.setSubscriptionId(subscription.getId());
            response.setPlan(subscription.getPlan().name());
            response.setAmount(subscription.getAmount());
            response.setStartDate(subscription.getStartDate());
            response.setExpiryDate(subscription.getExpiryDate());
            response.setPaymentStatus(subscription.getPaymentStatus().name());
            response.setSubscriptionStatus(computeSubscriptionStatus(subscription));
            if (subscription.getExpiryDate() != null) {
                response.setDaysRemaining(ChronoUnit.DAYS.between(LocalDate.now(), subscription.getExpiryDate()));
            }
        } else {
            response.setSubscriptionStatus("Payment Pending");
            response.setPaymentStatus(SellerSubscription.PaymentStatus.PENDING.name());
        }

        if (includePayments) {
            List<SellerPayment> payments = paymentRepository.findBySellerIdOrderByPaidAtDesc(seller.getId());
            List<SellerPaymentResponse> paymentResponses = new ArrayList<SellerPaymentResponse>();
            for (SellerPayment payment : payments) {
                paymentResponses.add(toPaymentResponse(payment, seller));
            }
            response.setPayments(paymentResponses);
        }
        return response;
    }

    private SellerPaymentResponse toPaymentResponse(SellerPayment payment, Seller seller) {
        SellerPaymentResponse response = new SellerPaymentResponse();
        response.setId(payment.getId());
        response.setTransactionCode(payment.getTransactionCode());
        response.setSellerId(payment.getSellerId());
        if (seller != null) {
            response.setSellerCode(seller.getSellerCode());
            response.setSellerName(seller.getOwnerName());
            response.setBusinessName(seller.getBusinessName());
        }
        response.setPlan(payment.getPlan() != null ? payment.getPlan().name() : null);
        response.setAmount(payment.getAmount());
        response.setPlanPriceSnapshot(payment.getPlanPriceSnapshot());
        response.setMethod(payment.getMethod() != null ? payment.getMethod().name() : null);
        response.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        response.setGatewayRef(payment.getGatewayRef());
        response.setNote(payment.getNote());
        response.setPaidAt(payment.getPaidAt());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        response.setCreatedBy(payment.getCreatedBy());
        return response;
    }

    private Seller resolveSellerForUser(User user, boolean createIfMissing) {
        Seller seller = sellerRepository.findByUserId(user.getId()).orElse(null);
        if (seller == null && user.getPhone() != null && user.getPhone().trim().length() > 0) {
            seller = sellerRepository.findByMobile(user.getPhone().trim()).orElse(null);
        }
        if (seller != null) {
            if (seller.getUserId() == null) {
                seller.setUserId(user.getId());
                sellerRepository.save(seller);
            }
            return seller;
        }
        if (!createIfMissing) {
            return null;
        }
        seller = new Seller();
        seller.setSellerCode(nextSellerCode());
        seller.setOwnerName(user.getFullName() != null && user.getFullName().trim().length() > 1
                ? user.getFullName().trim() : user.getUsername());
        seller.setMobile(user.getPhone().trim());
        seller.setEmail(user.getEmail());
        seller.setBusinessName(user.getFullName() != null && user.getFullName().trim().length() > 1
                ? user.getFullName().trim() : "Natural Drops");
        String address = user.getAddress();
        if (address == null || address.trim().isEmpty()) {
            address = user.getStreetArea() != null ? user.getStreetArea() : "Not provided";
        }
        seller.setBusinessAddress(address);
        seller.setArea(user.getStreetArea() != null && user.getStreetArea().trim().length() > 0 ? user.getStreetArea() : "NA");
        seller.setCity(user.getCity() != null && user.getCity().trim().length() > 0 ? user.getCity() : "NA");
        seller.setPincode(user.getPincode() != null && user.getPincode().trim().length() == 6 ? user.getPincode() : "000000");
        seller.setAccountStatus(Seller.AccountStatus.PENDING);
        seller.setUserId(user.getId());
        seller.setCreatedBy(user.getUsername());
        return sellerRepository.save(seller);
    }

    private SellerSubscriptionAccessResponse toAccessResponse(Seller seller, SellerSubscription subscription) {
        SellerSubscriptionAccessResponse response = new SellerSubscriptionAccessResponse();
        response.setMonthlyAmount(MONTHLY_AMOUNT);
        response.setYearlyAmount(YEARLY_AMOUNT);
        response.setBusinessName(seller != null ? seller.getBusinessName() : null);
        String status = computeSubscriptionStatus(subscription);
        response.setStatus(status);
        boolean subscribed = subscription != null
                && subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.SUCCESSFUL
                && subscription.getExpiryDate() != null
                && !subscription.getExpiryDate().isBefore(LocalDate.now());
        response.setSubscribed(subscribed);
        response.setCanWork(subscribed);
        if (subscription != null) {
            response.setPlan(subscription.getPlan() != null ? subscription.getPlan().name() : null);
            response.setAmount(subscription.getAmount());
            response.setStartDate(subscription.getStartDate());
            response.setExpiryDate(subscription.getExpiryDate());
            if (subscription.getExpiryDate() != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), subscription.getExpiryDate());
                response.setDaysRemaining(days);
                boolean reminder = subscribed && days >= 0 && days <= 5;
                response.setShowExpiryReminder(reminder);
                if (reminder) {
                    if (days == 0) {
                        response.setReminderMessage("Your subscription expires today. Please renew to keep selling without interruption.");
                    } else {
                        response.setReminderMessage("Your subscription expires in " + days
                                + " day" + (days == 1 ? "" : "s")
                                + " on " + subscription.getExpiryDate()
                                + ". Please renew before it ends.");
                    }
                }
            }
        }
        if (!subscribed) {
            if ("Expired".equals(status)) {
                response.setReminderMessage("Your subscription has expired. Subscribe again to continue using seller features.");
            } else {
                response.setReminderMessage("Subscribe to activate your seller account. You can use the app only after a successful subscription.");
            }
        }
        return response;
    }

    private String computeSubscriptionStatus(SellerSubscription subscription) {
        if (subscription == null || subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.PENDING
                || subscription.getExpiryDate() == null) {
            return "Payment Pending";
        }
        if (subscription.getPaymentStatus() == SellerSubscription.PaymentStatus.FAILED
                && (subscription.getExpiryDate() == null || subscription.getExpiryDate().isBefore(LocalDate.now()))) {
            return "Payment Pending";
        }
        LocalDate today = LocalDate.now();
        if (subscription.getExpiryDate().isBefore(today)) {
            return "Expired";
        }
        if (!subscription.getExpiryDate().isAfter(today.plusDays(7))) {
            return "Expiring Soon";
        }
        return "Active";
    }

    private Map<Long, Seller> indexSellers() {
        Map<Long, Seller> map = new HashMap<Long, Seller>();
        for (Seller seller : sellerRepository.findAll()) {
            map.put(seller.getId(), seller);
        }
        return map;
    }

    private Map<Long, SellerSubscription> indexSubscriptions() {
        Map<Long, SellerSubscription> map = new HashMap<Long, SellerSubscription>();
        for (SellerSubscription subscription : subscriptionRepository.findAll()) {
            map.put(subscription.getSellerId(), subscription);
        }
        return map;
    }

    private String percent(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        long value = Math.round((part * 100.0) / total);
        return value + "%";
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
