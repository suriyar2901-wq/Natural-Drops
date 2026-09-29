package com.naturaldrops.repository;

import com.naturaldrops.entity.SellerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerSubscriptionRepository extends JpaRepository<SellerSubscription, Long> {
    Optional<SellerSubscription> findBySellerId(Long sellerId);
}
