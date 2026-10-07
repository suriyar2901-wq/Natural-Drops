package com.naturaldrops.repository;

import com.naturaldrops.entity.CanEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CanEventRepository extends JpaRepository<CanEvent, Long> {
    List<CanEvent> findByCustomerIdOrderByOccurredAtDesc(Long customerId);

    List<CanEvent> findByCustomerIdInAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            List<Long> customerIds,
            LocalDateTime start,
            LocalDateTime end);
}
