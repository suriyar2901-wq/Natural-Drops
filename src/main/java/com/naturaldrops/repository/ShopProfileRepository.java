package com.naturaldrops.repository;

import com.naturaldrops.entity.ShopProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopProfileRepository extends JpaRepository<ShopProfile, Long> {
    Optional<ShopProfile> findBySellerUserId(Long sellerUserId);
}
