package com.naturaldrops.repository;

import com.naturaldrops.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long> {
    
    Optional<Setting> findBySettingKey(String key);
    
    boolean existsBySettingKey(String key);
}

