package com.naturaldrops.repository;

import com.naturaldrops.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
    
    List<StockHistory> findByMenuItemIdOrderByChangedAtDesc(Long menuItemId);
    
    List<StockHistory> findByOrderIdOrderByChangedAtDesc(Long orderId);
}

