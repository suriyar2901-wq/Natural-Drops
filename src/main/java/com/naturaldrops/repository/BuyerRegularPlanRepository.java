package com.naturaldrops.repository;

import com.naturaldrops.entity.BuyerRegularPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuyerRegularPlanRepository extends JpaRepository<BuyerRegularPlan, Long> {
    Optional<BuyerRegularPlan> findByBuyerId(Long buyerId);

    @Query("select distinct p from BuyerRegularPlan p left join fetch p.items where p.paused = false")
    List<BuyerRegularPlan> findActiveWithItems();

    @Query("select distinct p from BuyerRegularPlan p left join fetch p.items where p.buyerId = :buyerId")
    Optional<BuyerRegularPlan> findByBuyerIdWithItems(@Param("buyerId") Long buyerId);
}
