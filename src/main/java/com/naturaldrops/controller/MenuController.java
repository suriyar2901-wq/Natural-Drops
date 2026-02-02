package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.ProductImage;
import com.naturaldrops.entity.ProductVideo;
import com.naturaldrops.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class MenuController {
    
    private final MenuService menuService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuItem>>> getAllMenuItems() {
        List<MenuItem> menuItems = menuService.getAllMenuItems();
        return ResponseEntity.ok(ApiResponse.success(menuItems));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItem>> getMenuItemById(@PathVariable Long id) {
        MenuItem menuItem = menuService.getMenuItemById(id);
        return ResponseEntity.ok(ApiResponse.success(menuItem));
    }
    
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getMenuItemsByCategory(@PathVariable MenuItem.Category category) {
        List<MenuItem> menuItems = menuService.getMenuItemsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(menuItems));
    }
    
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getLowStockItems() {
        List<MenuItem> menuItems = menuService.getLowStockItems();
        return ResponseEntity.ok(ApiResponse.success(menuItems));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<MenuItem>> createMenuItem(@RequestBody MenuItem menuItem) {
        MenuItem createdItem = menuService.createMenuItem(menuItem);
        return ResponseEntity.ok(ApiResponse.success("Menu item created successfully", createdItem));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItem>> updateMenuItem(@PathVariable Long id, @RequestBody MenuItem menuItem) {
        MenuItem updatedItem = menuService.updateMenuItem(id, menuItem);
        return ResponseEntity.ok(ApiResponse.success("Menu item updated successfully", updatedItem));
    }
    
    @PutMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<MenuItem>> updateStock(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> payload) {
        Integer quantity = (Integer) payload.get("quantity");
        String changedBy = (String) payload.getOrDefault("changedBy", "admin");
        String notes = (String) payload.get("notes");
        
        MenuItem updatedItem = menuService.updateStock(id, quantity, changedBy, notes);
        return ResponseEntity.ok(ApiResponse.success("Stock updated successfully", updatedItem));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteMenuItem(@PathVariable Long id) {
        menuService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.success("Menu item deleted successfully", null));
    }
    
    // Product Image Endpoints
    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<ProductImage>>> getProductImages(@PathVariable Long id) {
        List<ProductImage> images = menuService.getProductImages(id);
        return ResponseEntity.ok(ApiResponse.success(images));
    }
    
    @PostMapping("/{id}/images")
    public ResponseEntity<ApiResponse<ProductImage>> addProductImage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String imageUrl = (String) payload.get("imageUrl");
        Boolean isPrimary = (Boolean) payload.getOrDefault("isPrimary", false);
        Integer displayOrder = (Integer) payload.getOrDefault("displayOrder", 0);
        
        ProductImage image = menuService.addProductImage(id, imageUrl, isPrimary, displayOrder);
        return ResponseEntity.ok(ApiResponse.success("Product image added successfully", image));
    }
    
    @DeleteMapping("/{menuItemId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Object>> deleteProductImage(
            @PathVariable Long menuItemId,
            @PathVariable Long imageId) {
        menuService.deleteProductImage(imageId);
        return ResponseEntity.ok(ApiResponse.success("Product image deleted successfully", null));
    }

    // Product Video Endpoints
    @GetMapping("/{id}/videos")
    public ResponseEntity<ApiResponse<List<ProductVideo>>> getProductVideos(@PathVariable Long id) {
        List<ProductVideo> videos = menuService.getProductVideos(id);
        return ResponseEntity.ok(ApiResponse.success(videos));
    }

    @PostMapping("/{id}/videos")
    public ResponseEntity<ApiResponse<ProductVideo>> addProductVideo(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String videoUrl = (String) payload.get("videoUrl");
        ProductVideo video = menuService.addProductVideo(id, videoUrl);
        return ResponseEntity.ok(ApiResponse.success("Product video added successfully", video));
    }

    @DeleteMapping("/{menuItemId}/videos/{videoId}")
    public ResponseEntity<ApiResponse<Object>> deleteProductVideo(
            @PathVariable Long menuItemId,
            @PathVariable Long videoId) {
        menuService.deleteProductVideo(videoId);
        return ResponseEntity.ok(ApiResponse.success("Product video deleted successfully", null));
    }
}

