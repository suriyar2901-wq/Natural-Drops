package com.naturaldrops.repository;

import com.naturaldrops.entity.CashfreePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CashfreePaymentRepository extends JpaRepository<CashfreePayment, Long> {

    Optional<CashfreePayment> findByCfOrderId(String cfOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CashfreePayment p WHERE p.cfOrderId = :cfOrderId")
    Optional<CashfreePayment> findByCfOrderIdForUpdate(@Param("cfOrderId") String cfOrderId);

    List<CashfreePayment> findByOrderIdAndBuyerUserIdOrderByCreatedAtDesc(Long orderId, Long buyerUserId);

    Optional<CashfreePayment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
            Long orderId, List<CashfreePayment.Status> statuses);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<CashfreePayment.Status> statuses);

    boolean existsByOrderIdAndPaymentAppliedTrue(Long orderId);
}
