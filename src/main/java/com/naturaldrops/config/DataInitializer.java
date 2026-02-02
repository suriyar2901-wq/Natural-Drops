package com.naturaldrops.config;

import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.User;
import com.naturaldrops.repository.MenuItemRepository;
import com.naturaldrops.repository.SettingRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final SettingRepository settingRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        try {
            // Initialize default admin user if not exists
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole(User.UserRole.seller);
                admin.setEmail("admin@naturaldrops.com");
                admin.setCreatedAt(LocalDateTime.now());
                admin.setCreatedBy("system");
                userRepository.save(admin);
                System.out.println("✅ Default admin user created: username=admin, password=admin123");
            }
            
            // Initialize menu items if empty
            if (menuItemRepository.count() == 0) {
                createInitialMenuItems();
                System.out.println("✅ Initial menu items created successfully");
            }
        } catch (Exception e) {
            System.err.println("⚠️  Warning: Could not initialize data: " + e.getMessage());
            System.err.println("⚠️  Tables might not exist yet. Run the application again or create data through the API");
            // Don't throw exception - allow application to start
        }
    }
    
    private void createInitialMenuItems() {
        // Water items
        menuItemRepository.save(createMenuItem("300ml Water", MenuItem.Category.water, 
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=300ml+Water", 30, new BigDecimal("5")));
        menuItemRepository.save(createMenuItem("500ml Water", MenuItem.Category.water,
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=500ml+Water", 24, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("1 Ltr Water", MenuItem.Category.water,
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=1L+Water", 15, new BigDecimal("15")));
        menuItemRepository.save(createMenuItem("2 Ltr Water", MenuItem.Category.water,
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=2L+Water", 9, new BigDecimal("25")));
        menuItemRepository.save(createMenuItem("5 Ltr Water", MenuItem.Category.water,
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=5L+Water", 1, new BigDecimal("50")));
        menuItemRepository.save(createMenuItem("20 Ltr Water", MenuItem.Category.water,
            "https://via.placeholder.com/200x200/4A90E2/FFFFFF?text=20L+Water", 1, new BigDecimal("70")));
        
        // ₹10 Beverages
        menuItemRepository.save(createMenuItem("₹10 7up", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/00B050/FFFFFF?text=7up+Rs10", 30, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("₹10 Coke", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/E74C3C/FFFFFF?text=Coke+Rs10", 30, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("₹10 Mango", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/F39C12/FFFFFF?text=Mango+Rs10", 30, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("₹10 Orange", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/FF6B6B/FFFFFF?text=Orange+Rs10", 30, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("₹10 Lemon Soda", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/F1C40F/FFFFFF?text=Lemon+Rs10", 30, new BigDecimal("10")));
        menuItemRepository.save(createMenuItem("₹10 Plain Soda", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/BDC3C7/FFFFFF?text=Soda+Rs10", 30, new BigDecimal("10")));
        
        // ₹20 Beverages
        menuItemRepository.save(createMenuItem("₹20 Coke", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/E74C3C/FFFFFF?text=Coke+Rs20", 24, new BigDecimal("20")));
        menuItemRepository.save(createMenuItem("₹20 Sprite", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/1ABC9C/FFFFFF?text=Sprite+Rs20", 24, new BigDecimal("20")));
        menuItemRepository.save(createMenuItem("₹20 7up", MenuItem.Category.beverage,
            "https://via.placeholder.com/200x200/00B050/FFFFFF?text=7up+Rs20", 24, new BigDecimal("20")));
    }
    
    private MenuItem createMenuItem(String name, MenuItem.Category category, String image, int quantity, BigDecimal rate) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setCategory(category);
        item.setImage(image);
        item.setStockQuantity(quantity);
        item.setLowStockThreshold(10);
        item.setRate(rate);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }
}

