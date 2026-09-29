package com.naturaldrops.repository;

import com.naturaldrops.entity.SellerInboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerInboxMessageRepository extends JpaRepository<SellerInboxMessage, Long> {
    List<SellerInboxMessage> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    long countBySellerIdAndIsRead(Long sellerId, Boolean isRead);
}
