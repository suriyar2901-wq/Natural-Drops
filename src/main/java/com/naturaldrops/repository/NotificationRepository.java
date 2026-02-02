package com.naturaldrops.repository;

import com.naturaldrops.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    List<Notification> findByIsReadOrderByCreatedAtDesc(Boolean isRead);
    
    List<Notification> findAllByOrderByCreatedAtDesc();
    
    Long countByIsRead(Boolean isRead);
}

