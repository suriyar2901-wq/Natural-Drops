package com.naturaldrops.repository;

import com.naturaldrops.entity.RegularBuyerItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegularBuyerItemRepository extends JpaRepository<RegularBuyerItem, Long> {
    List<RegularBuyerItem> findByPlanIdOrderByIdAsc(Long planId);

    void deleteByPlanId(Long planId);
}
