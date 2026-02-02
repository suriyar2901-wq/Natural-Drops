package com.naturaldrops.repository;

import com.naturaldrops.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    
    @Query("SELECT p FROM ProductImage p WHERE p.menuItem.id = :menuItemId ORDER BY p.isPrimary DESC, p.displayOrder ASC, p.id ASC")
    List<ProductImage> findByMenuItemIdOrderByDisplayOrderAsc(@Param("menuItemId") Long menuItemId);
    
    ProductImage findByMenuItemIdAndIsPrimaryTrue(Long menuItemId);
    
    void deleteByMenuItemId(Long menuItemId);
}

