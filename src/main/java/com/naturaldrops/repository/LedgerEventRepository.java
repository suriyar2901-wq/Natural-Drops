package com.naturaldrops.repository;

import com.naturaldrops.entity.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, Long> {
    List<LedgerEvent> findByCustomerIdOrderByOccurredAtDesc(Long customerId);
}
