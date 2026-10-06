package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.entity.BuyerRegularItem;
import com.naturaldrops.entity.BuyerRegularPlan;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.repository.BuyerRegularPlanRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BuyerRegularOrderService {

    private final BuyerRegularPlanRepository planRepository;
    private final UserRepository userRepository;
    private final SellerNetworkService sellerNetworkService;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public BuyerRegularPlan mine(User user) {
        requireBuyer(user);
        return planRepository.findByBuyerId(user.getId()).orElse(null);
    }

    @Transactional
    public BuyerRegularPlan save(User user, Map<String, Object> body) {
        requireBuyer(user);
        String weekDays = normalizeDays(stringValue(body.get("weekDays")));
        String deliveryTime = normalizeTime(stringValue(body.get("deliveryTime")));
        String address = stringValue(body.get("deliveryAddress")).trim();
        if (address.isEmpty()) {
            throw new BadRequestException("Delivery address is required");
        }
        List<BuyerRegularItem> incoming = readItems(body.get("items"));

        BuyerRegularPlan plan = planRepository.findByBuyerId(user.getId()).orElseGet(BuyerRegularPlan::new);
        boolean created = plan.getId() == null;
        plan.setBuyerId(user.getId());
        plan.setSellerUserId(sellerNetworkService.sellerUserIdForBuyer(user.getId()));
        plan.setWeekDays(weekDays);
        plan.setDeliveryTime(deliveryTime);
        plan.setDeliveryAddress(address);
        plan.setLatitude(doubleValue(body.get("latitude")));
        plan.setLongitude(doubleValue(body.get("longitude")));
        plan.setUpdatedAt(LocalDateTime.now());
        if (plan.getItems() == null) {
            plan.setItems(new ArrayList<BuyerRegularItem>());
        }
        plan.getItems().clear();
        BuyerRegularPlan saved = planRepository.save(plan);
        for (BuyerRegularItem item : incoming) {
            item.setPlan(saved);
            saved.getItems().add(item);
        }
        BuyerRegularPlan stored = planRepository.save(saved);
        if (created) {
            notifySeller(user, stored, "Regular order created", " created a new regular order.");
        }
        return stored;
    }

    @Transactional
    public BuyerRegularPlan setPaused(User user, boolean paused) {
        requireBuyer(user);
        BuyerRegularPlan plan = planRepository.findByBuyerId(user.getId())
                .orElseThrow(() -> new BadRequestException("Set a regular order first"));
        boolean alreadyPaused = plan.isPaused();
        plan.setPaused(paused);
        plan.setUpdatedAt(LocalDateTime.now());
        BuyerRegularPlan saved = planRepository.save(plan);
        if (paused && !alreadyPaused) {
            notifySeller(user, saved, "Regular order paused", " paused the regular order. No new delivery will be created until they resume.");
        } else if (!paused && alreadyPaused) {
            notifySeller(user, saved, "Regular order resumed", " resumed the regular order. Deliveries will continue.");
        }
        return saved;
    }

    private void notifySeller(User buyer, BuyerRegularPlan plan, String title, String action) {
        Long sellerUserId = plan.getSellerUserId() != null
                ? plan.getSellerUserId()
                : sellerNetworkService.sellerUserIdForBuyer(buyer.getId());
        String name = buyer.getFullName() != null && !buyer.getFullName().trim().isEmpty()
                ? buyer.getFullName().trim()
                : buyer.getUsername();
        StringBuilder message = new StringBuilder();
        message.append(name).append(action);
        String days = dayNames(plan.getWeekDays());
        if (!days.isEmpty()) {
            message.append(" Days: ").append(days).append(".");
        }
        if (plan.getDeliveryTime() != null && !plan.getDeliveryTime().trim().isEmpty()) {
            message.append(" Time: ").append(plan.getDeliveryTime()).append(".");
        }
        if (plan.getItems() != null && !plan.getItems().isEmpty()) {
            message.append(" Items: ");
            boolean first = true;
            for (BuyerRegularItem item : plan.getItems()) {
                if (!first) {
                    message.append(", ");
                }
                first = false;
                message.append(item.getItemName()).append(" x ").append(item.getQuantity());
            }
            message.append(".");
        }
        sellerNetworkService.postInboxMessage(sellerUserId, buyer.getId(), title, message.toString());
    }

    private String dayNames(String weekDays) {
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        if (weekDays == null || weekDays.trim().isEmpty()) {
            return "";
        }
        StringBuilder label = new StringBuilder();
        for (String part : weekDays.split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day < 0 || day > 6) {
                    continue;
                }
                if (label.length() > 0) {
                    label.append(", ");
                }
                label.append(names[day]);
            } catch (NumberFormatException ignored) {
                // skip a bad day token
            }
        }
        return label.toString();
    }

    @Transactional(readOnly = true)
    public List<BuyerRegularPlan> activePlans() {
        return planRepository.findActiveWithItems();
    }

    @Transactional(readOnly = true)
    public BuyerRegularPlan planWithItems(Long buyerId) {
        return planRepository.findByBuyerIdWithItems(buyerId).orElse(null);
    }

    public boolean dueOn(BuyerRegularPlan plan, LocalDate deliveryDate) {
        if (plan == null || plan.isPaused() || deliveryDate == null) {
            return false;
        }
        if (deliveryDate.equals(plan.getLastCreatedFor())) {
            return false;
        }
        return daySelected(plan.getWeekDays(), String.valueOf(deliveryDate.getDayOfWeek().getValue() % 7));
    }

    @Transactional
    public Order createScheduledOrder(BuyerRegularPlan plan, LocalDate deliveryDate) {
        User buyer = userRepository.findById(plan.getBuyerId())
                .orElseThrow(() -> new BadRequestException("Buyer not found"));
        BigDecimal subtotal = BigDecimal.ZERO;
        List<CreateOrderRequest.OrderItemRequest> items = new ArrayList<CreateOrderRequest.OrderItemRequest>();
        for (BuyerRegularItem source : plan.getItems()) {
            int quantity = source.getQuantity() == null ? 0 : source.getQuantity();
            BigDecimal rate = source.getRate() == null ? BigDecimal.ZERO : source.getRate();
            BigDecimal line = rate.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(line);
            CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
            item.setMenuItemId(source.getMenuItemId());
            item.setItemName(source.getItemName());
            item.setQuantity(quantity);
            item.setRate(rate);
            item.setCartQuantity(quantity);
            item.setSubtotal(line);
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new BadRequestException("Regular order has no items");
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax).add(new BigDecimal("20")).setScale(2, RoundingMode.HALF_UP);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setBuyerId(buyer.getId());
        request.setBuyerName(buyer.getFullName() != null && !buyer.getFullName().trim().isEmpty()
                ? buyer.getFullName()
                : buyer.getUsername());
        request.setBuyerPhone(buyer.getPhone());
        request.setBuyerAddress(buyer.getAddress());
        request.setDeliveryAddress(plan.getDeliveryAddress());
        request.setLatitude(plan.getLatitude());
        request.setLongitude(plan.getLongitude());
        request.setTotal(total);
        request.setScheduledDeliveryDate(deliveryDate.toString());
        request.setDeliveryTime(plan.getDeliveryTime());
        request.setNote("Regular order");
        request.setItems(items);
        return orderService.createOrder(request);
    }

    @Transactional
    public void markCreated(Long planId, LocalDate deliveryDate) {
        BuyerRegularPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return;
        }
        plan.setLastCreatedFor(deliveryDate);
        planRepository.save(plan);
    }

    private void requireBuyer(User user) {
        if (user == null || user.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Only a buyer can set a regular order");
        }
    }

    private String normalizeDays(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BadRequestException("Choose at least one delivery day");
        }
        Set<String> days = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (!token.matches("[0-6]")) {
                continue;
            }
            days.add(token);
        }
        if (days.isEmpty()) {
            throw new BadRequestException("Choose at least one delivery day");
        }
        StringBuilder builder = new StringBuilder();
        for (String day : days) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(day);
        }
        return builder.toString();
    }

    private String normalizeTime(String raw) {
        if (raw == null || !raw.trim().matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new BadRequestException("Choose a delivery time");
        }
        return raw.trim();
    }

    @SuppressWarnings("unchecked")
    private List<BuyerRegularItem> readItems(Object raw) {
        if (!(raw instanceof List)) {
            throw new BadRequestException("Add at least one item");
        }
        List<BuyerRegularItem> items = new ArrayList<BuyerRegularItem>();
        for (Object entry : (List<Object>) raw) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) entry;
            String name = stringValue(row.get("itemName")).trim();
            int quantity = intValue(row.get("quantity"));
            if (name.isEmpty() || quantity <= 0) {
                continue;
            }
            BuyerRegularItem item = new BuyerRegularItem();
            item.setMenuItemId(longValue(row.get("menuItemId")));
            item.setItemName(name);
            item.setQuantity(quantity);
            item.setRate(decimalValue(row.get("rate")));
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new BadRequestException("Add at least one item");
        }
        return items;
    }

    private boolean daySelected(String weekDays, String dayToken) {
        if (weekDays == null) {
            return false;
        }
        for (String part : weekDays.split(",")) {
            if (dayToken.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Double doubleValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).replace(".0", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0 : parsed.intValue();
    }

    private BigDecimal decimalValue(Object value) {
        try {
            return new BigDecimal(value == null ? "0" : String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
