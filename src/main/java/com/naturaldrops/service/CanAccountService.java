package com.naturaldrops.service;

import com.naturaldrops.entity.CanEvent;
import com.naturaldrops.entity.SellerCanAccount;
import com.naturaldrops.entity.SellerCanStockLog;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.CanEventRepository;
import com.naturaldrops.repository.SellerCanAccountRepository;
import com.naturaldrops.repository.SellerCanStockLogRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CanAccountService {

    private final SellerCanAccountRepository accountRepository;
    private final SellerCanStockLogRepository stockLogRepository;
    private final CanEventRepository canEventRepository;
    private final ShopCustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long sellerUserId) {
        return buildSummary(sellerUserId);
    }

    @Transactional
    public Map<String, Object> saveDepositRate(Long sellerUserId, Map<String, Object> body) {
        BigDecimal rate = money(body == null ? null : body.get("depositPerCan"), "Enter the can deposit amount");
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Can deposit amount cannot be negative");
        }
        SellerCanAccount account = ensure(sellerUserId);
        account.setDepositPerCan(rate);
        accountRepository.save(account);
        return buildSummary(sellerUserId);
    }

    @Transactional
    public Map<String, Object> adjustStock(Long sellerUserId, Map<String, Object> body) {
        int qty = positiveQty(body == null ? null : body.get("quantity"), "Enter how many cans to adjust");
        String direction = upper(body == null ? null : body.get("direction"), "ADD");
        String note = text(body == null ? null : body.get("note"));
        SellerCanAccount account = ensure(sellerUserId);
        int stock = count(account.getTotalStock());
        int delta;
        String copy;
        if ("REMOVE".equals(direction)) {
            if (qty > stock) {
                throw new BadRequestException("Cannot remove more cans than seller stock (" + stock + ")");
            }
            delta = -qty;
            copy = "Seller stock reduced by " + qty + " can(s)";
        } else if ("ADD".equals(direction)) {
            delta = qty;
            copy = "Seller stock added " + qty + " can(s)";
        } else {
            throw new BadRequestException("Choose add or remove for the stock adjustment");
        }
        account.setTotalStock(stock + delta);
        accountRepository.save(account);
        SellerCanStockLog log = new SellerCanStockLog();
        log.setSellerUserId(sellerUserId);
        log.setChangeAmount(delta);
        log.setCopy(note.isEmpty() ? copy : copy + ". " + note);
        log.setOccurredAt(LocalDateTime.now());
        stockLogRepository.save(log);
        return buildSummary(sellerUserId);
    }

    @Transactional
    public BigDecimal afterIssue(Long sellerUserId, ShopCustomer customer, int qty) {
        if (customer == null || sellerUserId == null || qty <= 0) {
            return BigDecimal.ZERO;
        }
        SellerCanAccount account = ensure(sellerUserId);
        int stock = count(account.getTotalStock());
        account.setTotalStock(Math.max(0, stock - qty));
        accountRepository.save(account);
        BigDecimal added = rate(account).multiply(BigDecimal.valueOf(qty));
        customer.setCanDeposit(money(customer.getCanDeposit()).add(added));
        customerRepository.save(customer);
        return added;
    }

    @Transactional
    public BigDecimal afterReturn(Long sellerUserId, ShopCustomer customer, int qty) {
        if (customer == null || sellerUserId == null || qty <= 0) {
            return BigDecimal.ZERO;
        }
        SellerCanAccount account = ensure(sellerUserId);
        account.setTotalStock(count(account.getTotalStock()) + qty);
        accountRepository.save(account);
        BigDecimal released = subtractDeposit(customer, rate(account).multiply(BigDecimal.valueOf(qty)));
        customerRepository.save(customer);
        return released;
    }

    @Transactional
    public ShopCustomer apply(Long sellerUserId, Long customerId, Map<String, Object> body) {
        if (body == null) {
            throw new BadRequestException("Choose damaged, missing, replacement, adjustment, or deposit");
        }
        ShopCustomer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (customer.getSellerUserId() == null || !customer.getSellerUserId().equals(sellerUserId)) {
            throw new ResourceNotFoundException("Customer not found");
        }
        String type = upper(body.get("type"), "");
        String note = text(body == null ? null : body.get("note"));
        if ("DAMAGED".equals(type)) {
            int qty = positiveQty(body.get("quantity"), "Enter how many damaged cans");
            takePending(customer, qty);
            SellerCanAccount account = ensure(sellerUserId);
            account.setDamaged(count(account.getDamaged()) + qty);
            accountRepository.save(account);
            BigDecimal released = subtractDeposit(customer, rate(account).multiply(BigDecimal.valueOf(qty)));
            customerRepository.save(customer);
            saveEvent(customer, "DAMAGED", -qty, qty, released.negate(), "Damaged " + qty + " can(s)", note);
            return customer;
        }
        if ("MISSING".equals(type)) {
            int qty = positiveQty(body.get("quantity"), "Enter how many missing cans");
            takePending(customer, qty);
            SellerCanAccount account = ensure(sellerUserId);
            account.setMissing(count(account.getMissing()) + qty);
            accountRepository.save(account);
            BigDecimal released = subtractDeposit(customer, rate(account).multiply(BigDecimal.valueOf(qty)));
            customerRepository.save(customer);
            saveEvent(customer, "MISSING", -qty, qty, released.negate(), "Missing " + qty + " can(s)", note);
            return customer;
        }
        if ("REPLACEMENT".equals(type)) {
            int qty = positiveQty(body.get("quantity"), "Enter how many cans to replace");
            int pending = count(customer.getEmptyCans());
            if (qty > pending) {
                throw new BadRequestException("Only " + pending + " can(s) are pending with this customer");
            }
            SellerCanAccount account = ensure(sellerUserId);
            int stock = count(account.getTotalStock());
            if (qty > stock) {
                throw new BadRequestException("Only " + stock + " can(s) are in seller stock");
            }
            account.setTotalStock(stock - qty);
            account.setDamaged(count(account.getDamaged()) + qty);
            accountRepository.save(account);
            saveEvent(customer, "REPLACEMENT", 0, qty, BigDecimal.ZERO, "Replaced " + qty + " can(s)", note);
            return customer;
        }
        if ("ADJUSTMENT".equals(type)) {
            int qty = positiveQty(body.get("quantity"), "Enter how many cans to adjust");
            String direction = upper(body.get("direction"), "ADD");
            SellerCanAccount account = ensure(sellerUserId);
            BigDecimal depositMove = rate(account).multiply(BigDecimal.valueOf(qty));
            if ("REMOVE".equals(direction)) {
                takePending(customer, qty);
                BigDecimal released = subtractDeposit(customer, depositMove);
                customerRepository.save(customer);
                saveEvent(customer, "ADJUSTMENT", -qty, qty, released.negate(), "Can adjustment -" + qty, note);
                return customer;
            }
            if (!"ADD".equals(direction)) {
                throw new BadRequestException("Choose add or remove for the can adjustment");
            }
            customer.setEmptyCans(count(customer.getEmptyCans()) + qty);
            customer.setCanDeposit(money(customer.getCanDeposit()).add(depositMove));
            customerRepository.save(customer);
            saveEvent(customer, "ADJUSTMENT", qty, qty, depositMove, "Can adjustment +" + qty, note);
            return customer;
        }
        if ("DEPOSIT".equals(type)) {
            BigDecimal amount = money(body.get("amount"), "Enter the can deposit amount");
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Can deposit amount must be greater than 0");
            }
            String direction = upper(body.get("direction"), "COLLECT");
            BigDecimal held = money(customer.getCanDeposit());
            if ("REFUND".equals(direction)) {
                if (amount.compareTo(held) > 0) {
                    throw new BadRequestException("Refund cannot exceed deposit held " + held.toPlainString());
                }
                customer.setCanDeposit(held.subtract(amount));
                customerRepository.save(customer);
                saveEvent(customer, "DEPOSIT", 0, 0, amount.negate(), "Can deposit refunded " + amount.toPlainString(), note);
                return customer;
            }
            if (!"COLLECT".equals(direction)) {
                throw new BadRequestException("Choose collect or refund for the can deposit");
            }
            customer.setCanDeposit(held.add(amount));
            customerRepository.save(customer);
            saveEvent(customer, "DEPOSIT", 0, 0, amount, "Can deposit collected " + amount.toPlainString(), note);
            return customer;
        }
        throw new BadRequestException("Choose damaged, missing, replacement, adjustment, or deposit");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> collectionReport(Long sellerUserId, String dateText) {
        LocalDate date;
        try {
            date = (dateText == null || dateText.trim().isEmpty()) ? LocalDate.now() : LocalDate.parse(dateText.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Choose a valid report date");
        }
        List<ShopCustomer> customers = customerRepository.findBySellerUserIdOrderByNameAsc(sellerUserId);
        Map<Long, ShopCustomer> byId = new LinkedHashMap<Long, ShopCustomer>();
        List<Long> ids = new ArrayList<Long>();
        for (ShopCustomer customer : customers) {
            byId.put(customer.getId(), customer);
            ids.add(customer.getId());
        }
        int returned = 0;
        int damaged = 0;
        int missing = 0;
        int replaced = 0;
        BigDecimal depositCollected = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (!ids.isEmpty()) {
            List<CanEvent> events = canEventRepository
                    .findByCustomerIdInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                            ids, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            for (CanEvent event : events) {
                String type = collectionType(event);
                if (type == null) {
                    continue;
                }
                int qty = eventQty(event);
                BigDecimal amount = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
                if ("RETURNED".equals(type)) {
                    returned += qty;
                } else if ("DAMAGED".equals(type)) {
                    damaged += qty;
                } else if ("MISSING".equals(type)) {
                    missing += qty;
                } else if ("REPLACEMENT".equals(type)) {
                    replaced += qty;
                } else if ("DEPOSIT".equals(type) && amount.compareTo(BigDecimal.ZERO) > 0) {
                    depositCollected = depositCollected.add(amount);
                }
                ShopCustomer customer = byId.get(event.getCustomerId());
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", event.getId());
                row.put("customerId", event.getCustomerId());
                row.put("name", customer == null ? "" : customer.getName());
                row.put("mobile", customer == null ? "" : customer.getMobile());
                row.put("eventType", type);
                row.put("quantity", qty);
                row.put("amount", amount);
                row.put("copy", event.getCopy());
                row.put("note", event.getNote());
                row.put("occurredAt", event.getOccurredAt());
                rows.add(row);
            }
        }
        rows.sort((left, right) -> String.valueOf(right.get("occurredAt")).compareTo(String.valueOf(left.get("occurredAt"))));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("date", date.toString());
        result.put("returned", returned);
        result.put("damaged", damaged);
        result.put("missing", missing);
        result.put("replaced", replaced);
        result.put("depositCollected", depositCollected);
        result.put("rows", rows);
        return result;
    }

    private Map<String, Object> buildSummary(Long sellerUserId) {
        SellerCanAccount account = load(sellerUserId);
        List<ShopCustomer> customers = customerRepository.findBySellerUserIdOrderByNameAsc(sellerUserId);
        int withCustomers = 0;
        BigDecimal depositHeld = BigDecimal.ZERO;
        for (ShopCustomer customer : customers) {
            withCustomers += count(customer.getEmptyCans());
            depositHeld = depositHeld.add(money(customer.getCanDeposit()));
        }
        List<Map<String, Object>> logs = new ArrayList<Map<String, Object>>();
        for (SellerCanStockLog log : stockLogRepository.findTop20BySellerUserIdOrderByOccurredAtDesc(sellerUserId)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", log.getId());
            row.put("changeAmount", log.getChangeAmount());
            row.put("copy", log.getCopy());
            row.put("occurredAt", log.getOccurredAt());
            logs.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("totalStock", count(account.getTotalStock()));
        result.put("depositPerCan", money(account.getDepositPerCan()));
        result.put("damaged", count(account.getDamaged()));
        result.put("missing", count(account.getMissing()));
        result.put("withCustomers", withCustomers);
        result.put("depositHeld", depositHeld);
        result.put("logs", logs);
        return result;
    }

    private SellerCanAccount load(Long sellerUserId) {
        return accountRepository.findBySellerUserId(sellerUserId).orElseGet(() -> blank(sellerUserId));
    }

    private SellerCanAccount ensure(Long sellerUserId) {
        return accountRepository.findBySellerUserId(sellerUserId).orElseGet(() -> accountRepository.save(blank(sellerUserId)));
    }

    private SellerCanAccount blank(Long sellerUserId) {
        SellerCanAccount account = new SellerCanAccount();
        account.setSellerUserId(sellerUserId);
        account.setTotalStock(0);
        account.setDepositPerCan(BigDecimal.ZERO);
        account.setDamaged(0);
        account.setMissing(0);
        return account;
    }

    private void takePending(ShopCustomer customer, int qty) {
        int pending = count(customer.getEmptyCans());
        if (qty > pending) {
            throw new BadRequestException("Only " + pending + " can(s) are pending with this customer");
        }
        customer.setEmptyCans(pending - qty);
    }

    private BigDecimal subtractDeposit(ShopCustomer customer, BigDecimal amount) {
        BigDecimal held = money(customer.getCanDeposit());
        BigDecimal cut = amount == null ? BigDecimal.ZERO : amount;
        if (cut.compareTo(BigDecimal.ZERO) < 0) {
            cut = BigDecimal.ZERO;
        }
        if (cut.compareTo(held) > 0) {
            cut = held;
        }
        customer.setCanDeposit(held.subtract(cut));
        return cut;
    }

    private void saveEvent(ShopCustomer customer, String type, int change, int quantity, BigDecimal amount, String copy, String note) {
        CanEvent event = new CanEvent();
        event.setCustomerId(customer.getId());
        event.setEventType(type);
        event.setChangeAmount(change);
        event.setQuantity(quantity);
        event.setAmount(amount == null ? BigDecimal.ZERO : amount);
        event.setNote(note == null || note.isEmpty() ? null : note);
        event.setCopy(note == null || note.isEmpty() ? copy : copy + ". " + note);
        event.setOccurredAt(LocalDateTime.now());
        canEventRepository.save(event);
    }

    private String collectionType(CanEvent event) {
        String type = event.getEventType() == null ? "" : event.getEventType().trim().toUpperCase();
        if (type.isEmpty()) {
            return event.getChangeAmount() != null && event.getChangeAmount() < 0 ? "RETURNED" : null;
        }
        if ("RETURNED".equals(type) || "DAMAGED".equals(type) || "MISSING".equals(type)
                || "REPLACEMENT".equals(type) || "DEPOSIT".equals(type)) {
            return type;
        }
        return null;
    }

    private int eventQty(CanEvent event) {
        if (event.getQuantity() != null) {
            return Math.abs(event.getQuantity());
        }
        return event.getChangeAmount() == null ? 0 : Math.abs(event.getChangeAmount());
    }

    private BigDecimal rate(SellerCanAccount account) {
        return money(account.getDepositPerCan());
    }

    private int positiveQty(Object value, String message) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BadRequestException(message);
        }
        int qty;
        try {
            qty = new BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (NumberFormatException ex) {
            throw new BadRequestException(message);
        }
        if (qty <= 0) {
            throw new BadRequestException(message);
        }
        return qty;
    }

    private BigDecimal money(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal money(Object value, String message) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BadRequestException(message);
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(message);
        }
    }

    private String upper(Object value, String fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return String.valueOf(value).trim().toUpperCase();
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        String note = String.valueOf(value).trim();
        return note.length() > 180 ? note.substring(0, 180) : note;
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }
}
