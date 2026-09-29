package com.naturaldrops.repository;

import com.naturaldrops.entity.ShopCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
    List<ShopCustomer> findBySellerUserIdOrderByNameAsc(Long sellerUserId);
    Optional<ShopCustomer> findBySellerUserIdAndMobile(Long sellerUserId, String mobile);
    Optional<ShopCustomer> findByBuyerUserId(Long buyerUserId);
    List<ShopCustomer> findByMobile(String mobile);
    boolean existsBySellerUserIdAndMobile(Long sellerUserId, String mobile);
}
