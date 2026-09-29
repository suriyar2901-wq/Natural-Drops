package com.naturaldrops.repository;

import com.naturaldrops.entity.SellerPayment;
import com.naturaldrops.entity.SellerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerPaymentRepository extends JpaRepository<SellerPayment, Long> {
    List<SellerPayment> findAllByOrderByPaidAtDesc();
    List<SellerPayment> findBySellerIdOrderByPaidAtDesc(Long sellerId);
    Optional<SellerPayment> findByTransactionCode(String transactionCode);
    List<SellerPayment> findByPaidAtBetweenOrderByPaidAtDesc(LocalDateTime start, LocalDateTime end);
    long countByStatus(SellerSubscription.PaymentStatus status);
}
