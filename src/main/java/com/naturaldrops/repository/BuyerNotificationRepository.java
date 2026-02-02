package com.naturaldrops.repository;

import com.naturaldrops.entity.BuyerNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuyerNotificationRepository extends JpaRepository<BuyerNotification, Long> {
    
    List<BuyerNotification> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    
    List<BuyerNotification> findByBuyerIdAndIsReadOrderByCreatedAtDesc(Long buyerId, Boolean isRead);
    
    Long countByBuyerIdAndIsRead(Long buyerId, Boolean isRead);
}

