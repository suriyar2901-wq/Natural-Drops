package com.naturaldrops.service;

import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.SellerInboxMessage;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.SellerInboxMessageRepository;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerNetworkService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;
    private final SellerInboxMessageRepository inboxRepository;
    private final ShopCustomerRepository shopCustomerRepository;

    public String generateCompanyCode(String companyName) {
        String prefix = sanitizePrefix(companyName);
        int suffix = 1;
        String code = prefix + "-" + String.format("%04d", suffix);
        while (sellerRepository.existsByCompanyCodeIgnoreCase(code)) {
            suffix++;
            code = prefix + "-" + String.format("%04d", suffix);
        }
        return code;
    }

    public void assignCompanyCode(Seller seller) {
        if (seller.getCompanyCode() != null && seller.getCompanyCode().trim().length() > 0) {
            return;
        }
        seller.setCompanyCode(generateCompanyCode(seller.getBusinessName()));
    }

    public Seller findByAnyCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new BadRequestException("Company code is required");
        }
        String code = rawCode.trim();
        Seller seller = sellerRepository.findByCompanyCodeIgnoreCase(code).orElse(null);
        if (seller == null) {
            seller = sellerRepository.findBySellerCode(code.toUpperCase()).orElse(null);
        }
        if (seller == null) {
            throw new BadRequestException("Invalid company code. Ask your seller for the correct code.");
        }
        return seller;
    }

    public Seller findSellerById(Long sellerId) {
        if (sellerId == null) {
            return null;
        }
        return sellerRepository.findById(sellerId).orElse(null);
    }

    public Long sellerUserIdForBuyer(Long buyerUserId) {
        if (buyerUserId == null) {
            return null;
        }
        User buyer = userRepository.findById(buyerUserId).orElse(null);
        if (buyer != null && buyer.getLinkedSellerId() != null) {
            Seller seller = sellerRepository.findById(buyer.getLinkedSellerId()).orElse(null);
            if (seller != null && seller.getUserId() != null) {
                return seller.getUserId();
            }
        }
        ShopCustomer customer = shopCustomerRepository.findByBuyerUserId(buyerUserId).orElse(null);
        return customer != null ? customer.getSellerUserId() : null;
    }

    public Seller findSellerForUser(User user) {
        if (user == null) {
            return null;
        }
        Seller seller = sellerRepository.findByUserId(user.getId()).orElse(null);
        if (seller == null && user.getPhone() != null) {
            seller = sellerRepository.findByMobile(user.getPhone().trim()).orElse(null);
        }
        return seller;
    }

    @Transactional
    public Seller createSellerAccount(User user, String companyName) {
        if (companyName == null || companyName.trim().length() < 2) {
            throw new BadRequestException("Shop name is required for a seller account");
        }
        Seller existing = findSellerForUser(user);
        if (existing != null) {
            if (existing.getCompanyCode() == null) {
                assignCompanyCode(existing);
                sellerRepository.save(existing);
            }
            existing.setUserId(user.getId());
            if (sellerGenderLabel(user.getGender()) != null) {
                existing.setGender(sellerGenderLabel(user.getGender()));
            }
            if (user.getDateOfBirth() != null) {
                existing.setDateOfBirth(user.getDateOfBirth());
            }
            if (user.getAadhaarNumber() != null) {
                existing.setAadhaarNumber(user.getAadhaarNumber());
            }
            return sellerRepository.save(existing);
        }
        Seller seller = new Seller();
        seller.setSellerCode(nextSellerCode());
        seller.setOwnerName(user.getFullName() != null && user.getFullName().trim().length() > 1
                ? user.getFullName().trim() : user.getUsername());
        seller.setMobile(user.getPhone());
        seller.setEmail(user.getEmail());
        seller.setBusinessName(companyName.trim());
        seller.setGender(sellerGenderLabel(user.getGender()));
        seller.setDateOfBirth(user.getDateOfBirth());
        seller.setAadhaarNumber(user.getAadhaarNumber());
        String address = user.getAddress() != null && user.getAddress().trim().length() > 0
                ? user.getAddress() : "Not provided";
        seller.setBusinessAddress(address);
        seller.setArea(user.getStreetArea() != null && user.getStreetArea().trim().length() > 0 ? user.getStreetArea() : "NA");
        seller.setCity(user.getCity() != null && user.getCity().trim().length() > 0 ? user.getCity() : "NA");
        seller.setPincode(user.getPincode() != null && user.getPincode().trim().length() == 6 ? user.getPincode() : "000000");
        seller.setAccountStatus(Seller.AccountStatus.ACTIVE);
        seller.setUserId(user.getId());
        seller.setCreatedBy(user.getUsername());
        assignCompanyCode(seller);
        return sellerRepository.save(seller);
    }

    @Transactional
    public void linkBuyerToCompany(User buyer, String companyCode) {
        linkBuyerToCompany(buyer, companyCode, true);
    }

    @Transactional
    public void linkBuyerToCompany(User buyer, String companyCode, boolean notify) {
        Seller seller = findByAnyCode(companyCode);
        buyer.setLinkedSellerId(seller.getId());
        userRepository.save(buyer);
        ensureShopCustomer(seller, buyer);
        if (notify) {
            notifyNewBuyer(seller, buyer);
        }
    }

    public List<User> listBuyers(Long sellerId) {
        return userRepository.findByLinkedSellerIdOrderByCreatedAtDesc(sellerId);
    }

    public java.util.Set<Long> buyerIdsForSeller(Seller seller) {
        java.util.Set<Long> ids = new java.util.HashSet<Long>();
        if (seller == null) {
            return ids;
        }
        for (User buyer : listBuyers(seller.getId())) {
            ids.add(buyer.getId());
        }
        return ids;
    }

    public List<Order> scopeOrders(List<Order> orders, User currentUser) {
        if (currentUser == null || currentUser.getRole() != User.UserRole.seller) {
            return orders;
        }
        Seller seller = findSellerForUser(currentUser);
        if (seller == null) {
            return new java.util.ArrayList<Order>();
        }
        java.util.Set<Long> buyerIds = buyerIdsForSeller(seller);
        List<Order> scoped = new java.util.ArrayList<Order>();
        if (orders == null) {
            return scoped;
        }
        for (Order order : orders) {
            if (order.getBuyerId() != null && buyerIds.contains(order.getBuyerId())) {
                scoped.add(order);
            }
        }
        return scoped;
    }

    public void attachOrderSellerNames(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Map<Long, String> namesByUserId = sellerNamesByUserId();
        for (Order order : orders) {
            if (order.getSellerUserId() == null) {
                continue;
            }
            order.setSellerBusinessName(namesByUserId.get(order.getSellerUserId()));
        }
    }

    public void attachShopNames(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<Long, String> namesBySellerId = new HashMap<Long, String>();
        Map<Long, String> namesByUserId = sellerNamesByUserId();
        for (Seller seller : sellerRepository.findAll()) {
            namesBySellerId.put(seller.getId(), sellerDisplayName(seller));
        }
        for (User user : users) {
            if (user.getRole() == User.UserRole.seller) {
                user.setShopName(namesByUserId.get(user.getId()));
            } else if (user.getRole() == User.UserRole.buyer) {
                String shopName = user.getLinkedSellerId() != null
                        ? namesBySellerId.get(user.getLinkedSellerId()) : null;
                if (shopName == null) {
                    Long sellerUserId = sellerUserIdForBuyer(user.getId());
                    if (sellerUserId != null) {
                        shopName = namesByUserId.get(sellerUserId);
                    }
                }
                user.setShopName(shopName);
            }
        }
    }

    private Map<Long, String> sellerNamesByUserId() {
        Map<Long, String> names = new HashMap<Long, String>();
        for (Seller seller : sellerRepository.findAll()) {
            if (seller.getUserId() != null) {
                names.put(seller.getUserId(), sellerDisplayName(seller));
            }
        }
        return names;
    }

    private String sellerDisplayName(Seller seller) {
        if (seller.getBusinessName() != null && !seller.getBusinessName().trim().isEmpty()) {
            return seller.getBusinessName().trim();
        }
        if (seller.getOwnerName() != null && !seller.getOwnerName().trim().isEmpty()) {
            return seller.getOwnerName().trim();
        }
        return seller.getSellerCode();
    }

    public List<SellerInboxMessage> listInbox(Long sellerId) {
        return inboxRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    public long unreadInboxCount(Long sellerId) {
        return inboxRepository.countBySellerIdAndIsRead(sellerId, false);
    }

    @Transactional
    public void postInboxMessage(Long sellerUserId, Long buyerUserId, String title, String message) {
        if (sellerUserId == null) {
            return;
        }
        User sellerUser = userRepository.findById(sellerUserId).orElse(null);
        Seller seller = findSellerForUser(sellerUser);
        if (seller == null || message == null || message.trim().isEmpty()) {
            return;
        }
        SellerInboxMessage inbox = new SellerInboxMessage();
        inbox.setSellerId(seller.getId());
        inbox.setBuyerUserId(buyerUserId);
        inbox.setTitle(title != null ? title : "Delivery reminder");
        inbox.setMessage(message.trim());
        inbox.setIsRead(false);
        inboxRepository.save(inbox);
    }

    @Transactional
    public void markInboxRead(Long sellerId, Long messageId) {
        SellerInboxMessage message = inboxRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!sellerId.equals(message.getSellerId())) {
            throw new BadRequestException("This message does not belong to the current seller");
        }
        message.setIsRead(true);
        inboxRepository.save(message);
    }

    private void notifyNewBuyer(Seller seller, User buyer) {
        SellerInboxMessage message = new SellerInboxMessage();
        message.setSellerId(seller.getId());
        message.setBuyerUserId(buyer.getId());
        message.setTitle("New buyer joined");
        message.setMessage(buildBuyerDetails(buyer, seller.getCompanyCode()));
        inboxRepository.save(message);
    }

    private void ensureShopCustomer(Seller seller, User buyer) {
        if (buyer.getPhone() == null || buyer.getPhone().trim().length() != 10) {
            return;
        }
        Long sellerUserId = seller.getUserId() != null ? seller.getUserId() : seller.getId();
        if (shopCustomerRepository.existsBySellerUserIdAndMobile(sellerUserId, buyer.getPhone().trim())) {
            return;
        }
        ShopCustomer customer = new ShopCustomer();
        customer.setSellerUserId(sellerUserId);
        customer.setBuyerUserId(buyer.getId());
        customer.setCustomerCode("CUS-" + String.format("%04d", shopCustomerRepository.count() + 1));
        customer.setName(buyer.getFullName() != null && buyer.getFullName().trim().length() > 0
                ? buyer.getFullName() : buyer.getUsername());
        customer.setMobile(buyer.getPhone().trim());
        customer.setHouse(buyer.getHouseDoorNo());
        customer.setArea(buyer.getStreetArea());
        customer.setCity(buyer.getCity());
        customer.setPin(buyer.getPincode());
        customer.setMoney(BigDecimal.ZERO);
        customer.setEmptyCans(0);
        shopCustomerRepository.save(customer);
    }

    private String buildBuyerDetails(User buyer, String companyCode) {
        StringBuilder builder = new StringBuilder();
        builder.append(buyer.getFullName() != null && buyer.getFullName().trim().length() > 0
                ? buyer.getFullName() : buyer.getUsername());
        builder.append(" created an account with company code ").append(companyCode).append(". ");
        builder.append("Username: ").append(buyer.getUsername()).append(". ");
        if (buyer.getPhone() != null) {
            builder.append("Mobile: ").append(buyer.getPhone()).append(". ");
        }
        if (buyer.getEmail() != null) {
            builder.append("Email: ").append(buyer.getEmail()).append(". ");
        }
        if (buyer.getHouseDoorNo() != null || buyer.getStreetArea() != null || buyer.getCity() != null) {
            builder.append("Address: ");
            if (buyer.getHouseDoorNo() != null) {
                builder.append(buyer.getHouseDoorNo()).append(", ");
            }
            if (buyer.getStreetArea() != null) {
                builder.append(buyer.getStreetArea()).append(", ");
            }
            if (buyer.getCity() != null) {
                builder.append(buyer.getCity()).append(" ");
            }
            if (buyer.getPincode() != null) {
                builder.append(buyer.getPincode());
            }
            builder.append(".");
        }
        return builder.toString().trim();
    }

    private String sellerGenderLabel(String gender) {
        if (gender == null) {
            return null;
        }
        if ("MALE".equalsIgnoreCase(gender) || "Male".equalsIgnoreCase(gender)) {
            return "Male";
        }
        if ("FEMALE".equalsIgnoreCase(gender) || "Female".equalsIgnoreCase(gender)) {
            return "Female";
        }
        if ("OTHER".equalsIgnoreCase(gender) || "Other".equalsIgnoreCase(gender)) {
            return "Other";
        }
        return null;
    }

    private String sanitizePrefix(String companyName) {
        if (companyName == null) {
            return "SHOP";
        }
        String letters = companyName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (letters.length() < 2) {
            return "SHOP";
        }
        return letters.length() > 6 ? letters.substring(0, 6) : letters;
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
}
