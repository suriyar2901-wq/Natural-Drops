package com.naturaldrops.repository;

import com.naturaldrops.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findFirstByEmail(String email);
    
    List<User> findByRole(User.UserRole role);
    
    boolean existsByUsername(String username);
}

