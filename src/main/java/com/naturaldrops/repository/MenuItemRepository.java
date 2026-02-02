package com.naturaldrops.repository;

import com.naturaldrops.entity.MenuItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    
    List<MenuItem> findByCategory(MenuItem.Category category);
    
    List<MenuItem> findByOrderByCreatedAtDesc();
    
    @Query("SELECT m FROM MenuItem m WHERE m.stockQuantity <= m.lowStockThreshold ORDER BY m.stockQuantity ASC")
    List<MenuItem> findLowStockItems();
    
    List<MenuItem> findByStockQuantityGreaterThan(Integer quantity);

    // --- Media eager loading (images + videos) ---
    @EntityGraph(attributePaths = {"images", "videos"})
    @Query("SELECT DISTINCT m FROM MenuItem m")
    List<MenuItem> findAllWithMedia();

    @EntityGraph(attributePaths = {"images", "videos"})
    @Query("SELECT DISTINCT m FROM MenuItem m WHERE m.category = :category")
    List<MenuItem> findByCategoryWithMedia(@Param("category") MenuItem.Category category);

    @EntityGraph(attributePaths = {"images", "videos"})
    @Query("SELECT DISTINCT m FROM MenuItem m WHERE m.stockQuantity <= m.lowStockThreshold ORDER BY m.stockQuantity ASC")
    List<MenuItem> findLowStockItemsWithMedia();

    @EntityGraph(attributePaths = {"images", "videos"})
    @Query("SELECT DISTINCT m FROM MenuItem m WHERE m.id = :id")
    Optional<MenuItem> findByIdWithMedia(@Param("id") Long id);
}

