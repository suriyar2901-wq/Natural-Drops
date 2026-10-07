package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.entity.CanEvent;
import com.naturaldrops.entity.LedgerEvent;
import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerInboxMessage;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.ShopProfile;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.CanEventRepository;
import com.naturaldrops.repository.LedgerEventRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import com.naturaldrops.repository.ShopProfileRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ShopService {

    private static final Pattern MOBILE = Pattern.compile("^[0-9]{10}$");

    private final ShopCustomerRepository customerRepository;
    private final LedgerEventRepository ledgerEventRepository;
    private final CanEventRepository canEventRepository;
    private final ShopProfileRepository shopProfileRepository;
    private final UserRepository userRepository;
    private final MenuService menuService;
    private final OrderService orderService;
    private final SellerNetworkService sellerNetworkService;
    private final AuthService authService;
    private final EmailService emailService;
    private final CanAccountService canAccountService;

    @Transactional(readOnly = true)
    public List<ShopCustomer> listCustomers(Long sellerUserId) {
        return customerRepository.findBySellerUserIdOrderByNameAsc(sellerUserId);
    }

    @Transactional(readOnly = true)
    public ShopCustomer getCustomer(Long sellerUserId, Long id) {
        ShopCustomer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        assertOwner(sellerUserId, customer);
        return customer;
    }

    @Transactional
    public ShopCustomer createCustomer(Long sellerUserId, Map<String, Object> body) {
        String name = required(body, "name", 2);
        String mobile = required(body, "mobile", 10);
        if (!MOBILE.matcher(mobile).matches()) {
            throw new BadRequestException("Mobile must be 10 digits");
        }
        if (customerRepository.existsBySellerUserIdAndMobile(sellerUserId, mobile)) {
            throw new BadRequestException("A customer with this mobile already exists");
        }
        ShopCustomer customer = new ShopCustomer();
        customer.setSellerUserId(sellerUserId);
        customer.setCustomerCode(nextCode());
        customer.setName(name);
        customer.setMobile(mobile);
        customer.setHouse(optional(body, "house"));
        customer.setArea(optional(body, "area"));
        customer.setCity(optional(body, "city"));
        customer.setPin(optional(body, "pin"));
        customer.setNote(optional(body, "note"));
        customer.setMoney(BigDecimal.ZERO);
        customer.setEmptyCans(0);
        List<User> buyers = userRepository.findByRole(User.UserRole.buyer);
        for (User buyer : buyers) {
            if (mobile.equals(buyer.getPhone())) {
                customer.setBuyerUserId(buyer.getId());
                break;
            }
        }
        return customerRepository.save(customer);
    }

    @Transactional
    public ShopCustomer updateCustomer(Long sellerUserId, Long id, Map<String, Object> body) {
        ShopCustomer customer = getCustomer(sellerUserId, id);
        String name = required(body, "name", 2);
        String mobile = required(body, "mobile", 10);
        if (!MOBILE.matcher(mobile).matches()) {
            throw new BadRequestException("Mobile must be 10 digits");
        }
        ShopCustomer existing = customerRepository.findBySellerUserIdAndMobile(sellerUserId, mobile).orElse(null);
        if (existing != null && !existing.getId().equals(id)) {
            throw new BadRequestException("A customer with this mobile already exists");
        }
        customer.setName(name);
        customer.setMobile(mobile);
        customer.setHouse(optional(body, "house"));
        customer.setArea(optional(body, "area"));
        customer.setCity(optional(body, "city"));
        customer.setPin(optional(body, "pin"));
        customer.setNote(optional(body, "note"));
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public List<LedgerEvent> listLedger(Long sellerUserId, Long customerId) {
        getCustomer(sellerUserId, customerId);
        return ledgerEventRepository.findByCustomerIdOrderByOccurredAtDesc(customerId);
    }

    @Transactional
    public ShopCustomer recordPayment(Long sellerUserId, Long customerId, Map<String, Object> body, String createdBy) {
        ShopCustomer customer = getCustomer(sellerUserId, customerId);
        BigDecimal amount = parseAmount(body == null ? null : body.get("amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }
        if (amount.compareTo(customer.getMoney()) > 0) {
            throw new BadRequestException("Amount cannot exceed outstanding balance " + customer.getMoney());
        }
        String method = body.get("method") == null ? "CASH" : String.valueOf(body.get("method")).trim().toUpperCase();
        customer.setMoney(customer.getMoney().subtract(amount));
        customerRepository.save(customer);
        saveLedger(customer.getId(), LedgerEvent.Kind.RECEIPT, amount, method, "PAY-" + System.currentTimeMillis(), createdBy);
        return customer;
    }

    @Transactional
    public ShopCustomer collectCans(Long sellerUserId, Long customerId, Integer qty) {
        ShopCustomer customer = getCustomer(sellerUserId, customerId);
        if (qty == null || qty <= 0) {
            throw new BadRequestException("Collect quantity must be greater than 0");
        }
        if (qty > customer.getEmptyCans()) {
            throw new BadRequestException("Cannot collect more cans than the customer holds");
        }
        customer.setEmptyCans(customer.getEmptyCans() - qty);
        BigDecimal released = canAccountService.afterReturn(sellerUserId, customer, qty);
        CanEvent event = new CanEvent();
        event.setCustomerId(customer.getId());
        event.setEventType("RETURNED");
        event.setChangeAmount(-qty);
        event.setQuantity(qty);
        event.setAmount(released.negate());
        event.setCopy("Returned " + qty + " empty can(s)");
        event.setOccurredAt(LocalDateTime.now());
        canEventRepository.save(event);
        return customer;
    }

    @Transactional(readOnly = true)
    public List<CanEvent> listCanEvents(Long sellerUserId, Long customerId) {
        getCustomer(sellerUserId, customerId);
        return canEventRepository.findByCustomerIdOrderByOccurredAtDesc(customerId);
    }

    @Transactional
    public Map<String, Object> createPhoneOrder(Long sellerUserId, Map<String, Object> body, String createdBy) {
        Long customerId = toLong(body.get("customerId"));
        Long menuItemId = body.get("menuItemId") == null ? null : toLong(body.get("menuItemId"));
        Integer qty = toInt(body.get("quantity"));
        boolean hasItems = body.get("items") instanceof List && !((List<?>) body.get("items")).isEmpty();
        if (!hasItems && (qty == null || qty < 1 || qty > 20)) {
            throw new BadRequestException("Quantity must be between 1 and 20");
        }
        ShopCustomer customer = getCustomer(sellerUserId, customerId);
        List<CreateOrderRequest.OrderItemRequest> items = buildPhoneOrderLines(body, menuItemId, qty);
        BigDecimal total = BigDecimal.ZERO;
        int extraCans = 0;
        for (CreateOrderRequest.OrderItemRequest line : items) {
            total = total.add(line.getSubtotal());
            if (line.getItemName() != null && line.getItemName().toLowerCase().contains("20")) {
                extraCans += line.getQuantity();
            }
        }
        String delivery = body.get("delivery") == null ? "Today" : String.valueOf(body.get("delivery")).trim();
        LocalDate deliveryDate = resolveDeliveryDate(delivery);
        String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
        LocalTime deliveryTime = LocalTime.of(9, 0);
        if (body.get("deliveryTime") != null && !String.valueOf(body.get("deliveryTime")).trim().isEmpty()) {
            try {
                deliveryTime = LocalTime.parse(String.valueOf(body.get("deliveryTime")).trim());
            } catch (DateTimeParseException ex) {
                throw new BadRequestException("Choose a valid delivery time");
            }
        }

        CreateOrderRequest request = new CreateOrderRequest();
        Long buyerId = customer.getBuyerUserId() != null ? customer.getBuyerUserId() : sellerUserId;
        request.setBuyerId(buyerId);
        request.setBuyerName(customer.getName());
        request.setBuyerPhone(customer.getMobile());
        request.setBuyerAddress(formatAddress(customer));
        request.setDeliveryAddress(formatAddress(customer) + " | Phone Order | " + deliveryDate);
        request.setTotal(total);
        request.setItems(items);
        Order order = orderService.createOrder(request);
        order = orderService.schedulePhoneDelivery(order.getId(), deliveryDate, deliveryTime, sellerUserId);

        customer.setMoney(customer.getMoney().add(total));
        customer.setEmptyCans(customer.getEmptyCans() + extraCans);
        customerRepository.save(customer);
        saveLedger(customer.getId(), LedgerEvent.Kind.BILL, total, "PAY_LATER", "ORD-" + order.getId(), createdBy);
        if (extraCans > 0) {
            BigDecimal deposit = canAccountService.afterIssue(sellerUserId, customer, extraCans);
            CanEvent event = new CanEvent();
            event.setCustomerId(customer.getId());
            event.setEventType("ISSUED");
            event.setChangeAmount(extraCans);
            event.setQuantity(extraCans);
            event.setAmount(deposit);
            event.setCopy("Issued " + extraCans + " can(s) with phone order #" + order.getId());
            event.setOccurredAt(LocalDateTime.now());
            canEventRepository.save(event);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("order", order);
        result.put("customer", customer);
        result.put("note", note);
        result.put("deliveryDate", deliveryDate.toString());
        return result;
    }

    private List<CreateOrderRequest.OrderItemRequest> buildPhoneOrderLines(Map<String, Object> body, Long menuItemId, Integer qty) {
        List<Map<String, Object>> requested = new ArrayList<Map<String, Object>>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List) {
            for (Object entry : (List<?>) rawItems) {
                if (entry instanceof Map) {
                    Map<String, Object> line = new HashMap<String, Object>();
                    for (Map.Entry<?, ?> field : ((Map<?, ?>) entry).entrySet()) {
                        line.put(String.valueOf(field.getKey()), field.getValue());
                    }
                    requested.add(line);
                }
            }
        }
        if (requested.isEmpty()) {
            Map<String, Object> single = new HashMap<String, Object>();
            single.put("menuItemId", menuItemId);
            single.put("quantity", qty);
            requested.add(single);
        }
        if (requested.isEmpty()) {
            throw new BadRequestException("Select at least one product");
        }

        List<CreateOrderRequest.OrderItemRequest> items = new ArrayList<CreateOrderRequest.OrderItemRequest>();
        for (Map<String, Object> requestedLine : requested) {
            Long lineMenuItemId = toLong(requestedLine.get("menuItemId"));
            Integer lineQty = toInt(requestedLine.get("quantity"));
            if (lineQty == null || lineQty < 1 || lineQty > 20) {
                throw new BadRequestException("Each product quantity must be between 1 and 20");
            }
            MenuItem item = menuService.getMenuItemById(lineMenuItemId);
            if (item.getRate() == null) {
                throw new BadRequestException("Selected product has no rate");
            }
            BigDecimal subtotal = item.getRate().multiply(new BigDecimal(lineQty));
            CreateOrderRequest.OrderItemRequest line = new CreateOrderRequest.OrderItemRequest();
            line.setMenuItemId(item.getId());
            line.setItemName(item.getName());
            line.setQuantity(lineQty);
            line.setRate(item.getRate());
            line.setCartQuantity(lineQty);
            line.setSubtotal(subtotal);
            items.add(line);
        }
        return items;
    }

    private LocalDate resolveDeliveryDate(String delivery) {
        LocalDate today = LocalDate.now();
        if (delivery == null || delivery.isEmpty() || "today".equalsIgnoreCase(delivery)) {
            return today;
        }
        if ("tomorrow".equalsIgnoreCase(delivery)) {
            return today.plusDays(1);
        }
        try {
            LocalDate date = LocalDate.parse(delivery);
            if (date.isBefore(today)) {
                throw new BadRequestException("Delivery date cannot be in the past");
            }
            return date;
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Choose Today, Tomorrow, or a delivery date");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> canLedger(Long sellerUserId) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (ShopCustomer customer : listCustomers(sellerUserId)) {
            int given = 0;
            int returned = 0;
            int damaged = 0;
            int missing = 0;
            for (CanEvent event : canEventRepository.findByCustomerIdOrderByOccurredAtDesc(customer.getId())) {
                String type = event.getEventType() == null ? "" : event.getEventType().trim().toUpperCase();
                int quantity = event.getChangeAmount() != null ? event.getChangeAmount() : 0;
                int abs = event.getQuantity() != null ? Math.abs(event.getQuantity()) : Math.abs(quantity);
                if (type.isEmpty()) {
                    if (quantity > 0) {
                        given += quantity;
                    } else {
                        returned += Math.abs(quantity);
                    }
                } else if ("ISSUED".equals(type)) {
                    given += abs;
                } else if ("RETURNED".equals(type)) {
                    returned += abs;
                } else if ("DAMAGED".equals(type)) {
                    damaged += abs;
                } else if ("MISSING".equals(type)) {
                    missing += abs;
                }
            }
            int toReturn = customer.getEmptyCans() != null ? customer.getEmptyCans() : 0;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("customerId", customer.getId());
            row.put("name", customer.getName());
            row.put("mobile", customer.getMobile());
            row.put("given", given);
            row.put("returned", returned);
            row.put("toReturn", toReturn);
            row.put("damaged", damaged);
            row.put("missing", missing);
            row.put("deposit", customer.getCanDeposit() == null ? BigDecimal.ZERO : customer.getCanDeposit());
            rows.add(row);
        }
        rows.sort((left, right) -> {
            int pending = Integer.compare(((Number) right.get("toReturn")).intValue(), ((Number) left.get("toReturn")).intValue());
            if (pending != 0) {
                return pending;
            }
            return String.valueOf(left.get("name")).compareToIgnoreCase(String.valueOf(right.get("name")));
        });
        return rows;
    }

    public Map<String, Object> companySummary(User user) {
        Seller seller = requireSellerRecord(user);
        if (seller.getCompanyCode() == null) {
            sellerNetworkService.assignCompanyCode(seller);
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("sellerId", seller.getId());
        result.put("companyName", seller.getBusinessName());
        result.put("companyCode", seller.getCompanyCode());
        result.put("sellerCode", seller.getSellerCode());
        result.put("buyerCount", sellerNetworkService.listBuyers(seller.getId()).size());
        result.put("unreadMessages", sellerNetworkService.unreadInboxCount(seller.getId()));
        result.put("profilePhoto", user != null ? user.getProfilePhoto() : null);
        return result;
    }

    @Transactional
    public Map<String, Object> createLinkedBuyer(User sellerUser, Map<String, Object> body) {
        Seller seller = requireSellerRecord(sellerUser);
        if (seller.getCompanyCode() == null) {
            sellerNetworkService.assignCompanyCode(seller);
        }
        String fullName = required(body, "fullName", 2);
        String phone = required(body, "phone", 10);
        if (!MOBILE.matcher(phone).matches()) {
            throw new BadRequestException("Mobile number must be 10 digits");
        }
        String email = required(body, "email", 5);
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BadRequestException("Email is invalid");
        }
        String username = required(body, "username", 3);
        String houseDoorNo = required(body, "houseDoorNo", 1);
        String streetArea = required(body, "streetArea", 2);
        String city = required(body, "city", 2);
        String district = required(body, "district", 2);
        String state = required(body, "state", 2);
        String pincode = required(body, "pincode", 6);
        if (!pincode.matches("^[0-9]{6}$")) {
            throw new BadRequestException("Pincode must be 6 digits");
        }
        username = username.trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists. Choose another username.");
        }
        User buyer = new User();
        buyer.setUsername(username);
        buyer.setFullName(fullName);
        buyer.setPassword(authService.encodePassword(java.util.UUID.randomUUID().toString() + "Aa1"));
        buyer.setMustSetPassword(true);
        buyer.setRole(User.UserRole.buyer);
        buyer.setStatus(User.UserStatus.APPROVED);
        buyer.setIsActive(true);
        buyer.setEmail(email);
        buyer.setPhone(phone);
        buyer.setHouseDoorNo(houseDoorNo);
        buyer.setStreetArea(streetArea);
        buyer.setCity(city);
        buyer.setDistrict(district);
        buyer.setState(state);
        buyer.setPincode(pincode);
        buyer.setCreatedAt(LocalDateTime.now());
        buyer.setCreatedBy(sellerUser.getUsername());
        buyer.setLinkedSellerId(seller.getId());
        User saved = userRepository.save(buyer);
        sellerNetworkService.linkBuyerToCompany(saved, seller.getCompanyCode(), false);
        Map<String, String> invite = authService.createInvite(saved);
        String resetLink = invite.get("resetLink");
        String shareMessage = "Your buyer account for " + seller.getBusinessName()
                + " is ready. Username: " + username
                + ". Create your password here: " + resetLink;
        boolean buyerEmailSent = emailService.sendBuyerInvite(email, fullName, username, seller.getBusinessName(), resetLink);
        boolean sellerEmailSent = emailService.sendBuyerInviteCopy(
                sellerUser.getEmail(),
                sellerUser.getFullName() != null ? sellerUser.getFullName() : sellerUser.getUsername(),
                username,
                fullName,
                seller.getBusinessName(),
                resetLink
        );
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("user", saved);
        result.put("username", username);
        result.put("companyName", seller.getBusinessName());
        result.put("companyCode", seller.getCompanyCode());
        result.put("inviteLink", resetLink);
        result.put("otp", invite.get("otp"));
        result.put("shareMessage", shareMessage);
        result.put("whatsappUrl", "https://wa.me/91" + phone + "?text=" + urlEncode(shareMessage));
        result.put("smsUrl", "sms:+91" + phone + "?body=" + urlEncode(shareMessage));
        result.put("emailSent", Boolean.valueOf(buyerEmailSent));
        result.put("sellerEmailSent", Boolean.valueOf(sellerEmailSent));
        return result;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    @Transactional(readOnly = true)
    public List<User> listLinkedBuyers(User user) {
        Seller seller = requireSellerRecord(user);
        return sellerNetworkService.listBuyers(seller.getId());
    }

    @Transactional(readOnly = true)
    public List<SellerInboxMessage> listInbox(User user) {
        Seller seller = requireSellerRecord(user);
        return sellerNetworkService.listInbox(seller.getId());
    }

    @Transactional
    public void markInboxRead(User user, Long messageId) {
        Seller seller = requireSellerRecord(user);
        sellerNetworkService.markInboxRead(seller.getId(), messageId);
    }

    private Seller requireSellerRecord(User user) {
        Seller seller = sellerNetworkService.findSellerForUser(user);
        if (seller == null) {
            throw new BadRequestException("Seller company profile is not ready yet");
        }
        return seller;
    }

    @Transactional(readOnly = true)
    public ShopProfile getProfile(Long sellerUserId) {
        ShopProfile profile = shopProfileRepository.findBySellerUserId(sellerUserId).orElse(null);
        if (profile != null) {
            return profile;
        }
        profile = new ShopProfile();
        profile.setSellerUserId(sellerUserId);
        User user = userRepository.findById(sellerUserId).orElse(null);
        if (user != null) {
            profile.setOwnerName(user.getFullName());
            profile.setBusinessName("Natural Drops");
        }
        return profile;
    }

    @Transactional
    public ShopProfile saveProfile(Long sellerUserId, ShopProfile incoming) {
        ShopProfile profile = shopProfileRepository.findBySellerUserId(sellerUserId).orElse(new ShopProfile());
        profile.setSellerUserId(sellerUserId);
        profile.setBusinessName(incoming.getBusinessName());
        profile.setAddress(incoming.getAddress());
        profile.setOwnerName(incoming.getOwnerName());
        profile.setAltMobile(incoming.getAltMobile());
        profile.setEmail(incoming.getEmail());
        if (incoming.getQrData() != null) {
            profile.setQrData(incoming.getQrData());
        }
        if (incoming.getOpenTime() != null || incoming.getCloseTime() != null) {
            String openTime = normalizeClock(incoming.getOpenTime());
            String closeTime = normalizeClock(incoming.getCloseTime());
            if (openTime == null || closeTime == null) {
                throw new BadRequestException("Shop open and close time are required");
            }
            if (!LocalTime.parse(closeTime).isAfter(LocalTime.parse(openTime))) {
                throw new BadRequestException("Shop close time must be after the open time");
            }
            profile.setOpenTime(openTime);
            profile.setCloseTime(closeTime);
            String openDays = normalizeDays(incoming.getOpenDays());
            if (openDays == null) {
                throw new BadRequestException("Select at least one open day");
            }
            profile.setOpenDays(openDays);
            profile.setLeaveDates(normalizeLeaves(incoming.getLeaveDates()));
            profile.setShowHoursToBuyer(Boolean.TRUE.equals(incoming.getShowHoursToBuyer()));
        }
        return shopProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buyerSummary(Long buyerUserId, String phone) {
        ShopCustomer customer = customerRepository.findByBuyerUserId(buyerUserId).orElse(null);
        if (customer == null && phone != null && phone.trim().length() == 10) {
            List<ShopCustomer> all = customerRepository.findAll();
            for (ShopCustomer item : all) {
                if (phone.equals(item.getMobile())) {
                    customer = item;
                    break;
                }
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("due", customer != null ? customer.getMoney() : BigDecimal.ZERO);
        result.put("emptyCans", customer != null ? customer.getEmptyCans() : 0);
        result.put("customer", customer);
        result.put("ledger", customer != null ? ledgerEventRepository.findByCustomerIdOrderByOccurredAtDesc(customer.getId()) : new ArrayList<LedgerEvent>());
        result.put("canEvents", customer != null ? canEventRepository.findByCustomerIdOrderByOccurredAtDesc(customer.getId()) : new ArrayList<CanEvent>());
        ShopProfile profile = customer != null
                ? shopProfileRepository.findBySellerUserId(customer.getSellerUserId()).orElse(null)
                : null;
        result.put("sellerQr", profile != null ? profile.getQrData() : null);
        result.put("sellerBusiness", profile != null ? profile.getBusinessName() : "Natural Drops");
        Seller seller = null;
        User buyer = userRepository.findById(buyerUserId).orElse(null);
        if (buyer != null && buyer.getLinkedSellerId() != null) {
            seller = sellerNetworkService.findSellerById(buyer.getLinkedSellerId());
        }
        if (seller == null && customer != null && customer.getSellerUserId() != null) {
            seller = sellerNetworkService.findSellerForUser(userRepository.findById(customer.getSellerUserId()).orElse(null));
        }
        User sellerUser = null;
        if (seller != null && seller.getUserId() != null) {
            sellerUser = userRepository.findById(seller.getUserId()).orElse(null);
        }
        result.put("companyName", seller != null && seller.getBusinessName() != null
                ? seller.getBusinessName()
                : (profile != null ? profile.getBusinessName() : null));
        result.put("companyCode", seller != null ? seller.getCompanyCode() : null);
        result.put("sellerProfilePhoto", sellerUser != null ? sellerUser.getProfilePhoto() : null);
        if (profile == null && seller != null && seller.getUserId() != null) {
            profile = shopProfileRepository.findBySellerUserId(seller.getUserId()).orElse(null);
        }
        putShopHours(result, profile);
        return result;
    }

    @Transactional
    public ShopCustomer buyerClaimPayment(Long buyerUserId, String phone, Map<String, Object> body) {
        Map<String, Object> summary = buyerSummary(buyerUserId, phone);
        ShopCustomer customer = (ShopCustomer) summary.get("customer");
        if (customer == null) {
            throw new ResourceNotFoundException("No shop account is linked to this buyer yet");
        }
        return recordPayment(customer.getSellerUserId(), customer.getId(), body, "buyer-claim");
    }

    private void putShopHours(Map<String, Object> result, ShopProfile profile) {
        boolean showToBuyer = profile != null && Boolean.TRUE.equals(profile.getShowHoursToBuyer());
        String openTime = showToBuyer && profile != null ? normalizeClock(profile.getOpenTime()) : null;
        String closeTime = showToBuyer && profile != null ? normalizeClock(profile.getCloseTime()) : null;
        String openDays = profile != null && profile.getOpenDays() != null ? profile.getOpenDays() : "0,1,2,3,4,5,6";
        String leaveDates = showToBuyer && profile != null ? profile.getLeaveDates() : null;
        result.put("shopOpenTime", openTime);
        result.put("shopCloseTime", closeTime);
        result.put("shopOpenDays", showToBuyer && profile != null ? profile.getOpenDays() : null);
        result.put("shopLeaveDates", leaveDates);
        if (openTime == null || closeTime == null) {
            result.put("shopOpenNow", Boolean.TRUE);
            result.put("shopNextOpenLabel", null);
            return;
        }
        java.util.Set<Integer> days = daySet(openDays);
        java.util.Set<LocalDate> leaves = leaveSet(leaveDates);
        LocalTime open = LocalTime.parse(openTime);
        LocalTime close = LocalTime.parse(closeTime);
        LocalDateTime now = LocalDateTime.now();
        boolean openNow = isOpenAt(now.toLocalDate(), now.toLocalTime(), open, close, days, leaves);
        result.put("shopOpenNow", Boolean.valueOf(openNow));
        result.put("shopNextOpenLabel", openNow ? null : nextOpenLabel(now, open, close, days, leaves));
    }

    private boolean isOpenAt(LocalDate date, LocalTime time, LocalTime open, LocalTime close,
                             java.util.Set<Integer> days, java.util.Set<LocalDate> leaves) {
        if (leaves.contains(date) || !days.contains(jsWeekday(date))) {
            return false;
        }
        return !time.isBefore(open) && time.isBefore(close);
    }

    private String nextOpenLabel(LocalDateTime now, LocalTime open, LocalTime close,
                                 java.util.Set<Integer> days, java.util.Set<LocalDate> leaves) {
        for (int offset = 0; offset < 60; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            if (leaves.contains(date) || !days.contains(jsWeekday(date))) {
                continue;
            }
            if (offset == 0 && !now.toLocalTime().isBefore(close)) {
                continue;
            }
            String when;
            if (offset == 0) {
                when = "today";
            } else if (offset == 1) {
                when = "tomorrow";
            } else {
                String weekday = date.getDayOfWeek().name();
                String month = date.getMonth().name();
                when = weekday.substring(0, 1) + weekday.substring(1).toLowerCase()
                        + " " + date.getDayOfMonth() + " "
                        + month.substring(0, 1) + month.substring(1, 3).toLowerCase();
            }
            return when + " at " + formatAmPm(open);
        }
        return "soon";
    }

    private int jsWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }

    private java.util.Set<Integer> daySet(String value) {
        java.util.Set<Integer> days = new java.util.HashSet<Integer>();
        if (value == null || value.trim().isEmpty()) {
            for (int day = 0; day <= 6; day++) {
                days.add(Integer.valueOf(day));
            }
            return days;
        }
        for (String part : value.split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day >= 0 && day <= 6) {
                    days.add(Integer.valueOf(day));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return days;
    }

    private java.util.Set<LocalDate> leaveSet(String value) {
        java.util.Set<LocalDate> leaves = new java.util.HashSet<LocalDate>();
        if (value == null || value.trim().isEmpty()) {
            return leaves;
        }
        for (String part : value.split(",")) {
            try {
                leaves.add(LocalDate.parse(part.trim()));
            } catch (DateTimeParseException ignored) {
                // skip
            }
        }
        return leaves;
    }

    private String normalizeDays(String value) {
        java.util.Set<Integer> days = daySet(value == null ? "" : value);
        if (value != null && value.trim().isEmpty()) {
            return null;
        }
        if (days.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int day = 0; day <= 6; day++) {
            if (!days.contains(Integer.valueOf(day))) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(day);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String normalizeLeaves(String value) {
        java.util.List<String> dates = new java.util.ArrayList<String>();
        for (LocalDate date : leaveSet(value)) {
            dates.add(date.toString());
        }
        java.util.Collections.sort(dates);
        StringBuilder builder = new StringBuilder();
        for (String date : dates) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(date);
        }
        return builder.toString();
    }

    private String normalizeClock(String value) {
        if (value == null || !value.matches("^\\d{2}:\\d{2}$")) {
            return null;
        }
        try {
            LocalTime.parse(value);
            return value;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String formatAmPm(LocalTime time) {
        int hour = time.getHour();
        String suffix = hour >= 12 ? "PM" : "AM";
        int display = hour % 12;
        if (display == 0) {
            display = 12;
        }
        return display + ":" + String.format("%02d", time.getMinute()) + " " + suffix;
    }

    private void saveLedger(Long customerId, LedgerEvent.Kind kind, BigDecimal amount, String method, String reference, String createdBy) {
        LedgerEvent event = new LedgerEvent();
        event.setCustomerId(customerId);
        event.setKind(kind);
        event.setAmount(amount);
        event.setMethod(method);
        event.setReference(reference);
        event.setOccurredAt(LocalDateTime.now());
        event.setCreatedBy(createdBy);
        ledgerEventRepository.save(event);
    }

    private void assertOwner(Long sellerUserId, ShopCustomer customer) {
        if (!sellerUserId.equals(customer.getSellerUserId())) {
            throw new BadRequestException("This customer does not belong to the current seller");
        }
    }

    private String nextCode() {
        return "CUS-" + String.format("%04d", customerRepository.count() + 1);
    }

    private String required(Map<String, Object> body, String key, int min) {
        String value = body == null || body.get(key) == null ? null : String.valueOf(body.get(key));
        if (value == null || value.trim().length() < min) {
            throw new BadRequestException(key + " is required");
        }
        return value.trim();
    }

    private String optional(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        String value = String.valueOf(body.get(key)).trim();
        return value.isEmpty() || "null".equals(value) ? null : value;
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BadRequestException("Amount is required");
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (Exception ex) {
            throw new BadRequestException("Amount is invalid");
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            throw new BadRequestException("Missing numeric field");
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String formatAddress(ShopCustomer customer) {
        StringBuilder builder = new StringBuilder();
        if (customer.getHouse() != null) {
            builder.append(customer.getHouse()).append(", ");
        }
        if (customer.getArea() != null) {
            builder.append(customer.getArea()).append(", ");
        }
        if (customer.getCity() != null) {
            builder.append(customer.getCity()).append(" ");
        }
        if (customer.getPin() != null) {
            builder.append(customer.getPin());
        }
        return builder.toString().trim();
    }
}
