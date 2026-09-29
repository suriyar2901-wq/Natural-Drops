package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.RegularBuyerItem;
import com.naturaldrops.entity.RegularBuyerPlan;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.MenuItemRepository;
import com.naturaldrops.repository.RegularBuyerItemRepository;
import com.naturaldrops.repository.RegularBuyerPlanRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegularOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.05");
    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("20");

    private final RegularBuyerPlanRepository planRepository;
    private final RegularBuyerItemRepository itemRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final SellerNetworkService sellerNetworkService;
    private final OrderService orderService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlans(User sellerUser) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        LocalDate today = LocalDate.now();
        for (RegularBuyerPlan plan : planRepository.findBySellerUserIdOrderByIdAsc(sellerUser.getId())) {
            if (!Boolean.TRUE.equals(plan.getActive())) {
                continue;
            }
            rows.add(toRow(plan, today));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> todayPrompt(User sellerUser) {
        LocalDate today = LocalDate.now();
        List<String> names = new ArrayList<String>();
        for (RegularBuyerPlan plan : planRepository.findBySellerUserIdOrderByIdAsc(sellerUser.getId())) {
            if (!dueNow(plan, today)) {
                continue;
            }
            User buyer = userRepository.findById(plan.getBuyerUserId()).orElse(null);
            names.add(buyerName(buyer));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("buyerCount", names.size());
        result.put("buyerNames", names);
        return result;
    }

    @Transactional
    public Map<String, Object> savePlan(User sellerUser, Long buyerId, Map<String, Object> body) {
        Seller seller = requireSeller(sellerUser);
        User buyer = linkedBuyer(seller, buyerId);
        RegularBuyerPlan plan = planRepository.findBySellerUserIdAndBuyerUserId(sellerUser.getId(), buyerId)
                .orElseGet(RegularBuyerPlan::new);
        plan.setSellerUserId(sellerUser.getId());
        plan.setBuyerUserId(buyerId);
        plan.setUpdatedAt(LocalDateTime.now());
        List<RegularBuyerItem> items = readItems(seller, body);
        if (!items.isEmpty()) {
            LocalTime promptTime = parsePromptTime(body == null ? null : body.get("promptTime"));
            if (promptTime == null) {
                throw new BadRequestException("Set the time for the daily message");
            }
            plan.setPromptTime(promptTime);
            LocalTime deliveryTime = parsePromptTime(body == null ? null : body.get("deliveryTime"));
            if (deliveryTime == null) {
                throw new BadRequestException("Set the delivery time");
            }
            plan.setDeliveryTime(deliveryTime);
            Object notes = body == null ? null : body.get("notes");
            plan.setNotes(notes == null ? null : String.valueOf(notes).trim());
        }
        plan.setActive(!items.isEmpty());
        RegularBuyerPlan saved = planRepository.save(plan);
        itemRepository.deleteByPlanId(saved.getId());
        for (RegularBuyerItem item : items) {
            item.setPlanId(saved.getId());
            itemRepository.save(item);
        }
        return toRow(saved, LocalDate.now());
    }

    public Map<String, Object> createTodayOrders(User sellerUser) {
        LocalDate today = LocalDate.now();
        List<Long> orderIds = new ArrayList<Long>();
        List<String> skipped = new ArrayList<String>();
        for (RegularBuyerPlan plan : planRepository.findBySellerUserIdOrderByIdAsc(sellerUser.getId())) {
            if (!dueNow(plan, today)) {
                continue;
            }
            try {
                Order order = createOne(sellerUser, plan, today);
                orderIds.add(order.getId());
            } catch (Exception ex) {
                User buyer = userRepository.findById(plan.getBuyerUserId()).orElse(null);
                skipped.add(buyerName(buyer) + ": " + ex.getMessage());
            }
        }
        if (!orderIds.isEmpty()) {
            sellerNetworkService.postInboxMessage(
                    sellerUser.getId(),
                    null,
                    "Regular orders created",
                    "Today's regular orders are created. Buyers have been notified."
            );
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("created", orderIds.size());
        result.put("orderIds", orderIds);
        result.put("skipped", skipped);
        return result;
    }

    @Transactional
    public void promptDueSellers() {
        LocalDate today = LocalDate.now();
        Map<Long, Integer> dueBySeller = new LinkedHashMap<Long, Integer>();
        for (RegularBuyerPlan plan : planRepository.findByActiveTrue()) {
            if (!dueNow(plan, today) || today.equals(plan.getLastPromptDate())) {
                continue;
            }
            Integer count = dueBySeller.get(plan.getSellerUserId());
            dueBySeller.put(plan.getSellerUserId(), count == null ? 1 : count + 1);
            plan.setLastPromptDate(today);
            planRepository.save(plan);
        }
        for (Map.Entry<Long, Integer> entry : dueBySeller.entrySet()) {
            sellerNetworkService.postInboxMessage(
                    entry.getKey(),
                    null,
                    "Regular orders",
                    "It is the time you set. Create today's regular orders for " + entry.getValue()
                            + " buyer" + (entry.getValue() == 1 ? "" : "s")
                            + "? Open My Buyers and tap Yes, create orders."
            );
        }
    }

    private Order createOne(User sellerUser, RegularBuyerPlan plan, LocalDate today) {
        User buyer = userRepository.findById(plan.getBuyerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));
        List<RegularBuyerItem> lines = itemRepository.findByPlanIdOrderByIdAsc(plan.getId());
        if (lines.isEmpty()) {
            throw new BadRequestException("No products set");
        }
        CreateOrderRequest request = new CreateOrderRequest();
        request.setBuyerId(buyer.getId());
        request.setBuyerName(buyerName(buyer));
        request.setBuyerPhone(buyer.getPhone());
        request.setBuyerAddress(addressOf(buyer));
        request.setDeliveryAddress(addressOf(buyer));
        applyDeliverySlot(request, plan);
        request.setNote(plan.getNotes());
        BigDecimal subtotal = BigDecimal.ZERO;
        List<CreateOrderRequest.OrderItemRequest> items = new ArrayList<CreateOrderRequest.OrderItemRequest>();
        for (RegularBuyerItem line : lines) {
            BigDecimal lineTotal = line.getRate().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
            item.setMenuItemId(line.getMenuItemId());
            item.setItemName(line.getItemName());
            item.setQuantity(line.getQuantity());
            item.setCartQuantity(line.getQuantity());
            item.setRate(line.getRate());
            item.setSubtotal(lineTotal);
            items.add(item);
        }
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        request.setItems(items);
        request.setTotal(subtotal.add(tax).add(DELIVERY_CHARGE).setScale(2, RoundingMode.HALF_UP));
        Order order = orderService.createOrder(request);
        String when = order.getEstimatedDelivery() == null
                ? "today"
                : order.getEstimatedDelivery().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' hh:mm a"));
        String sellerMessage = "Regular order #" + order.getId() + " created for " + buyerName(buyer) + ". Delivery " + when + ".";
        String buyerMessage = "Your regular order #" + order.getId() + " is created. Delivery " + when + ".";
        notificationService.setOrderNotificationMessage(order.getId(), sellerMessage);
        notificationService.createBuyerMessage(order, buyerMessage);
        plan.setLastOrderDate(today);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return order;
    }

    private void applyDeliverySlot(CreateOrderRequest request, RegularBuyerPlan plan) {
        LocalTime deliveryTime = plan.getDeliveryTime() != null ? plan.getDeliveryTime() : LocalTime.of(9, 0);
        deliveryTime = deliveryTime.withSecond(0).withNano(0);
        LocalDate date = LocalDate.now();
        if (!date.atTime(deliveryTime).isAfter(LocalDateTime.now())) {
            date = date.plusDays(1);
        }
        request.setScheduledDeliveryDate(date.toString());
        request.setDeliveryTime(deliveryTime.toString());
    }

    private boolean readyForToday(RegularBuyerPlan plan, LocalDate today) {
        if (plan == null || !Boolean.TRUE.equals(plan.getActive()) || today.equals(plan.getLastOrderDate())) {
            return false;
        }
        return !itemRepository.findByPlanIdOrderByIdAsc(plan.getId()).isEmpty();
    }

    private boolean dueNow(RegularBuyerPlan plan, LocalDate today) {
        if (!readyForToday(plan, today)) {
            return false;
        }
        LocalTime promptTime = plan.getPromptTime() != null ? plan.getPromptTime() : LocalTime.of(7, 0);
        return !LocalTime.now().isBefore(promptTime);
    }

    private LocalTime parsePromptTime(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(String.valueOf(value).trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Set a valid time");
        }
    }

    private List<RegularBuyerItem> readItems(Seller seller, Map<String, Object> body) {
        Object raw = body == null ? null : body.get("items");
        if (!(raw instanceof List)) {
            throw new BadRequestException("Add at least one product");
        }
        List<RegularBuyerItem> items = new ArrayList<RegularBuyerItem>();
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) entry;
            Long menuItemId = longValue(row.get("menuItemId"));
            Integer quantity = intValue(row.get("quantity"));
            if (menuItemId == null || quantity == null || quantity <= 0) {
                continue;
            }
            MenuItem menuItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (menuItem.getSellerId() != null && !menuItem.getSellerId().equals(seller.getId())) {
                throw new BadRequestException("That product is not in your shop");
            }
            RegularBuyerItem item = new RegularBuyerItem();
            item.setMenuItemId(menuItem.getId());
            item.setItemName(menuItem.getName());
            item.setQuantity(quantity);
            item.setRate(menuItem.getRate());
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> toRow(RegularBuyerPlan plan, LocalDate today) {
        User buyer = userRepository.findById(plan.getBuyerUserId()).orElse(null);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (RegularBuyerItem item : itemRepository.findByPlanIdOrderByIdAsc(plan.getId())) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("menuItemId", item.getMenuItemId());
            row.put("itemName", item.getItemName());
            row.put("quantity", item.getQuantity());
            row.put("rate", item.getRate());
            items.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("buyerId", plan.getBuyerUserId());
        result.put("buyerName", buyerName(buyer));
        result.put("active", plan.getActive());
        result.put("orderedToday", today.equals(plan.getLastOrderDate()));
        result.put("promptTime", plan.getPromptTime() != null ? plan.getPromptTime().withSecond(0).withNano(0).toString() : null);
        result.put("deliveryTime", plan.getDeliveryTime() != null ? plan.getDeliveryTime().withSecond(0).withNano(0).toString() : null);
        result.put("notes", plan.getNotes());
        result.put("items", items);
        return result;
    }

    private User linkedBuyer(Seller seller, Long buyerId) {
        for (User buyer : sellerNetworkService.listBuyers(seller.getId())) {
            if (buyer.getId().equals(buyerId)) {
                return buyer;
            }
        }
        throw new BadRequestException("This buyer is not linked to your shop");
    }

    private Seller requireSeller(User sellerUser) {
        Seller seller = sellerNetworkService.findSellerForUser(sellerUser);
        if (seller == null) {
            throw new BadRequestException("Seller company profile is not ready yet");
        }
        return seller;
    }

    private String buyerName(User buyer) {
        if (buyer == null) {
            return "Buyer";
        }
        if (buyer.getFullName() != null && !buyer.getFullName().trim().isEmpty()) {
            return buyer.getFullName().trim();
        }
        return buyer.getUsername();
    }

    private String addressOf(User buyer) {
        StringBuilder address = new StringBuilder();
        append(address, buyer.getHouseDoorNo());
        append(address, buyer.getStreetArea());
        append(address, buyer.getCity());
        append(address, buyer.getDistrict());
        append(address, buyer.getState());
        append(address, buyer.getPincode());
        if (address.length() == 0 && buyer.getAddress() != null) {
            return buyer.getAddress();
        }
        return address.length() == 0 ? "Regular order" : address.toString();
    }

    private void append(StringBuilder address, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (address.length() > 0) {
            address.append(", ");
        }
        address.append(value.trim());
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
