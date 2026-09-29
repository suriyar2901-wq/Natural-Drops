package com.naturaldrops.repository;

import com.naturaldrops.entity.RegularBuyerPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegularBuyerPlanRepository extends JpaRepository<RegularBuyerPlan, Long> {
    List<RegularBuyerPlan> findBySellerUserIdOrderByIdAsc(Long sellerUserId);

    Optional<RegularBuyerPlan> findBySellerUserIdAndBuyerUserId(Long sellerUserId, Long buyerUserId);

    List<RegularBuyerPlan> findByActiveTrue();
}
