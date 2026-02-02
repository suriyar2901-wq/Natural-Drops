package com.naturaldrops.repository;

import com.naturaldrops.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    Optional<PasswordResetToken> findByOtp(String otp);
    
    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.userId = :userId AND prt.used = false AND prt.expiryTime > :now")
    List<PasswordResetToken> findByUserIdAndNotUsed(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiryTime < :now OR prt.used = true")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}

