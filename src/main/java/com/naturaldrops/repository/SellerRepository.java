package com.naturaldrops.repository;

import com.naturaldrops.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findBySellerCode(String sellerCode);
    Optional<Seller> findByCompanyCodeIgnoreCase(String companyCode);
    Optional<Seller> findByUserId(Long userId);
    Optional<Seller> findByMobile(String mobile);
    List<Seller> findAllByMobile(String mobile);
    List<Seller> findByEmailIgnoreCase(String email);
    boolean existsByMobile(String mobile);
    boolean existsByCompanyCodeIgnoreCase(String companyCode);
}
