package com.naturaldrops.service;

import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.ProductImage;
import com.naturaldrops.entity.ProductVideo;
import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.StockHistory;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.MenuItemRepository;
import com.naturaldrops.repository.ProductImageRepository;
import com.naturaldrops.repository.ProductVideoRepository;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MenuService {
    
    private final MenuItemRepository menuItemRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVideoRepository productVideoRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final SellerNetworkService sellerNetworkService;
    private final SellerRepository sellerRepository;
    
    @Transactional(readOnly = true)
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAllWithMedia();
    }

    @Transactional(readOnly = true)
    public List<MenuItem> getMenuItemsForUser(User user) {
        if (user == null || user.getRole() == User.UserRole.admin) {
            List<MenuItem> items = getAllMenuItems();
            if (user != null && user.getRole() == User.UserRole.admin) {
                attachSellerNames(items);
            }
            return items;
        }
        if (user.getRole() == User.UserRole.seller) {
            Seller seller = sellerNetworkService.findSellerForUser(user);
            if (seller == null) {
                return java.util.Collections.emptyList();
            }
            return menuItemRepository.findBySellerIdWithMedia(seller.getId());
        }
        if (user.getLinkedSellerId() != null) {
            return menuItemRepository.findBySellerIdWithMedia(user.getLinkedSellerId());
        }
        return menuItemRepository.findUnassignedWithMedia();
    }
    
    public MenuItem getMenuItemById(Long id) {
        return menuItemRepository.findByIdWithMedia(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));
    }
    
    public List<MenuItem> getMenuItemsByCategory(MenuItem.Category category) {
        return menuItemRepository.findByCategoryWithMedia(category);
    }
    
    public List<MenuItem> getLowStockItems(User user) {
        List<MenuItem> items = menuItemRepository.findLowStockItemsWithMedia();
        if (user != null && user.getRole() == User.UserRole.admin) {
            attachSellerNames(items);
        }
        return items;
    }

    public void attachSellerName(MenuItem item) {
        if (item == null) return;
        attachSellerNames(java.util.Collections.singletonList(item));
    }

    private void attachSellerNames(List<MenuItem> items) {
        if (items == null || items.isEmpty()) return;
        Set<Long> sellerIds = new HashSet<Long>();
        for (MenuItem item : items) {
            if (item.getSellerId() != null) {
                sellerIds.add(item.getSellerId());
            }
        }
        if (sellerIds.isEmpty()) return;
        Map<Long, String> names = new HashMap<Long, String>();
        for (Seller seller : sellerRepository.findAllById(sellerIds)) {
            names.put(seller.getId(), sellerDisplayName(seller));
        }
        for (MenuItem item : items) {
            if (item.getSellerId() != null) {
                item.setSellerName(names.get(item.getSellerId()));
            }
        }
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
    
    @Transactional
    public MenuItem createMenuItem(MenuItem menuItem, User currentUser) {
        if (currentUser != null && currentUser.getRole() == User.UserRole.seller) {
            Seller seller = sellerNetworkService.findSellerForUser(currentUser);
            if (seller == null) {
                throw new BadRequestException("Seller profile is not ready. Complete company details first.");
            }
            menuItem.setSellerId(seller.getId());
        }
        menuItem.setCreatedAt(LocalDateTime.now());
        menuItem.setUpdatedAt(LocalDateTime.now());
        if (menuItem.getLowStockThreshold() == null) {
            menuItem.setLowStockThreshold(10);
        }
        menuItem.setDescription(normalizeDescription(menuItem.getDescription()));
        MenuItem saved = menuItemRepository.save(menuItem);
        return getMenuItemById(saved.getId());
    }
    
    @Transactional
    public MenuItem updateMenuItem(Long id, MenuItem menuItemDetails, User currentUser) {
        MenuItem menuItem = getMenuItemById(id);
        assertCanManage(menuItem, currentUser);
        
        menuItem.setName(menuItemDetails.getName());
        menuItem.setCategory(menuItemDetails.getCategory());
        
        // Update image field - always update if provided (even if null to clear it)
        // This ensures the image field is properly updated in the database
        menuItem.setImage(menuItemDetails.getImage());
        
        menuItem.setDescription(normalizeDescription(menuItemDetails.getDescription()));

        menuItem.setStockQuantity(menuItemDetails.getStockQuantity());
        menuItem.setLowStockThreshold(menuItemDetails.getLowStockThreshold());
        menuItem.setRate(menuItemDetails.getRate());
        menuItem.setUpdatedAt(LocalDateTime.now());
        
        // Save and return the updated item
        // The image field will be properly persisted and returned in the response
        MenuItem saved = menuItemRepository.save(menuItem);
        return getMenuItemById(saved.getId());
    }

    private String normalizeDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Product description must be at most 500 characters");
        }
        return trimmed;
    }
    
    @Transactional
    public MenuItem updateStock(Long id, Integer quantity, String changedBy, String notes) {
        MenuItem menuItem = getMenuItemById(id);
        Integer quantityBefore = menuItem.getStockQuantity();
        Integer quantityChange = quantity - quantityBefore;
        
        menuItem.setStockQuantity(quantity);
        menuItem.setUpdatedAt(LocalDateTime.now());
        
        // Record stock history
        StockHistory history = new StockHistory();
        history.setMenuItemId(menuItem.getId());
        history.setChangeType(StockHistory.ChangeType.manual_adjustment);
        history.setQuantityChange(quantityChange);
        history.setQuantityBefore(quantityBefore);
        history.setQuantityAfter(quantity);
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes(notes);
        stockHistoryRepository.save(history);
        
        return menuItemRepository.save(menuItem);
    }
    
    @Transactional
    public void deductStock(Long menuItemId, Integer quantity, Long orderId, String changedBy) {
        MenuItem menuItem = getMenuItemById(menuItemId);
        Integer quantityBefore = menuItem.getStockQuantity() == null ? 0 : menuItem.getStockQuantity();
        int requested = quantity == null ? 0 : quantity;

        if (requested <= 0) {
            return;
        }
        if (quantityBefore < requested) {
            throw new IllegalStateException("Insufficient stock for menu item: " + menuItem.getName()
                    + ". Available: " + quantityBefore + ", requested: " + requested);
        }
        
        menuItem.setStockQuantity(quantityBefore - requested);
        menuItem.setUpdatedAt(LocalDateTime.now());
        menuItemRepository.save(menuItem);
        
        // Record stock history
        StockHistory history = new StockHistory();
        history.setMenuItemId(menuItemId);
        history.setOrderId(orderId);
        history.setChangeType(StockHistory.ChangeType.order_confirmed);
        history.setQuantityChange(-requested);
        history.setQuantityBefore(quantityBefore);
        history.setQuantityAfter(quantityBefore - requested);
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes("Stock deducted for order #" + orderId);
        stockHistoryRepository.save(history);
    }

    /**
     * Deducts up to the available stock and never fails the order when stock is short.
     * Returns the quantity actually deducted.
     */
    @Transactional
    public int deductAvailableStock(Long menuItemId, Integer quantity, Long orderId, String changedBy) {
        MenuItem menuItem = getMenuItemById(menuItemId);
        int quantityBefore = menuItem.getStockQuantity() == null ? 0 : menuItem.getStockQuantity();
        int requested = quantity == null ? 0 : quantity;
        int deducted = Math.min(Math.max(quantityBefore, 0), Math.max(requested, 0));
        if (deducted <= 0) {
            return 0;
        }

        menuItem.setStockQuantity(quantityBefore - deducted);
        menuItem.setUpdatedAt(LocalDateTime.now());
        menuItemRepository.save(menuItem);

        StockHistory history = new StockHistory();
        history.setMenuItemId(menuItemId);
        history.setOrderId(orderId);
        history.setChangeType(StockHistory.ChangeType.order_confirmed);
        history.setQuantityChange(-deducted);
        history.setQuantityBefore(quantityBefore);
        history.setQuantityAfter(quantityBefore - deducted);
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes("Stock deducted for order #" + orderId
                + (deducted < requested ? " (requested " + requested + ", short by " + (requested - deducted) + ")" : ""));
        stockHistoryRepository.save(history);
        return deducted;
    }
    
    @Transactional
    public void restoreStock(Long menuItemId, Integer quantity, Long orderId, String changedBy) {
        MenuItem menuItem = getMenuItemById(menuItemId);
        Integer quantityBefore = menuItem.getStockQuantity();
        
        menuItem.setStockQuantity(quantityBefore + quantity);
        menuItem.setUpdatedAt(LocalDateTime.now());
        menuItemRepository.save(menuItem);
        
        // Record stock history
        StockHistory history = new StockHistory();
        history.setMenuItemId(menuItemId);
        history.setOrderId(orderId);
        history.setChangeType(StockHistory.ChangeType.order_canceled);
        history.setQuantityChange(quantity);
        history.setQuantityBefore(quantityBefore);
        history.setQuantityAfter(quantityBefore + quantity);
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes("Stock restored from canceled order #" + orderId);
        stockHistoryRepository.save(history);
    }
    
    @Transactional
    public void deleteMenuItem(Long id, User currentUser) {
        MenuItem menuItem = getMenuItemById(id);
        assertCanManage(menuItem, currentUser);
        menuItemRepository.deleteById(id);
    }

    private void assertCanManage(MenuItem menuItem, User currentUser) {
        if (currentUser == null || currentUser.getRole() == User.UserRole.admin) {
            return;
        }
        if (currentUser.getRole() != User.UserRole.seller) {
            throw new BadRequestException("Only sellers can change products");
        }
        Seller seller = sellerNetworkService.findSellerForUser(currentUser);
        if (seller == null || menuItem.getSellerId() == null || !seller.getId().equals(menuItem.getSellerId())) {
            throw new BadRequestException("You can update only your own products");
        }
    }
    
    // Product Image Management
    public List<ProductImage> getProductImages(Long menuItemId) {
        return productImageRepository.findByMenuItemIdOrderByDisplayOrderAsc(menuItemId);
    }
    
    @Transactional
    public ProductImage addProductImage(Long menuItemId, String imageUrl, Boolean isPrimary, Integer displayOrder) {
        MenuItem menuItem = getMenuItemById(menuItemId);
        
        // If this is set as primary, unset other primary images
        if (isPrimary != null && isPrimary) {
            ProductImage existingPrimary = productImageRepository.findByMenuItemIdAndIsPrimaryTrue(menuItemId);
            if (existingPrimary != null) {
                existingPrimary.setIsPrimary(false);
                productImageRepository.save(existingPrimary);
            }
        }
        
        ProductImage productImage = new ProductImage();
        productImage.setMenuItem(menuItem);
        productImage.setImageUrl(imageUrl);
        productImage.setIsPrimary(isPrimary != null ? isPrimary : false);
        productImage.setDisplayOrder(displayOrder != null ? displayOrder : 0);
        productImage.setCreatedAt(LocalDateTime.now());
        
        return productImageRepository.save(productImage);
    }
    
    @Transactional
    public void deleteProductImage(Long imageId) {
        if (!productImageRepository.existsById(imageId)) {
            throw new ResourceNotFoundException("Product image not found with id: " + imageId);
        }
        productImageRepository.deleteById(imageId);
    }

    // Product Video Management
    public List<ProductVideo> getProductVideos(Long menuItemId) {
        return productVideoRepository.findByMenuItemIdOrderByIdAsc(menuItemId);
    }

    @Transactional
    public ProductVideo addProductVideo(Long menuItemId, String videoUrl) {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("videoUrl is required");
        }
        MenuItem menuItem = getMenuItemById(menuItemId);

        ProductVideo video = new ProductVideo();
        video.setMenuItem(menuItem);
        video.setVideoUrl(videoUrl);
        video.setCreatedAt(LocalDateTime.now());

        return productVideoRepository.save(video);
    }

    @Transactional
    public void deleteProductVideo(Long videoId) {
        if (!productVideoRepository.existsById(videoId)) {
            throw new ResourceNotFoundException("Product video not found with id: " + videoId);
        }
        productVideoRepository.deleteById(videoId);
    }
}

