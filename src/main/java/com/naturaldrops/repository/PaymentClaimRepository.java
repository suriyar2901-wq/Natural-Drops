package com.naturaldrops.repository;

import com.naturaldrops.entity.PaymentClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentClaimRepository extends JpaRepository<PaymentClaim, Long> {

    List<PaymentClaim> findByKindAndStatusOrderByCreatedAtDesc(PaymentClaim.Kind kind, PaymentClaim.Status status);

    List<PaymentClaim> findBySellerUserIdAndKindAndStatusOrderByCreatedAtDesc(
            Long sellerUserId, PaymentClaim.Kind kind, PaymentClaim.Status status);

    List<PaymentClaim> findByClaimantUserIdAndKindOrderByCreatedAtDesc(Long claimantUserId, PaymentClaim.Kind kind);

    Optional<PaymentClaim> findByOrderIdAndStatus(Long orderId, PaymentClaim.Status status);

    Optional<PaymentClaim> findBySellerIdAndKindAndStatus(Long sellerId, PaymentClaim.Kind kind, PaymentClaim.Status status);
}
