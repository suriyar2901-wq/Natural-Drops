package com.naturaldrops.repository;

import com.naturaldrops.entity.SellerCanStockLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerCanStockLogRepository extends JpaRepository<SellerCanStockLog, Long> {
    List<SellerCanStockLog> findTop20BySellerUserIdOrderByOccurredAtDesc(Long sellerUserId);
}
