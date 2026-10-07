package com.naturaldrops.repository;

import com.naturaldrops.entity.SellerCanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerCanAccountRepository extends JpaRepository<SellerCanAccount, Long> {
    Optional<SellerCanAccount> findBySellerUserId(Long sellerUserId);
}
