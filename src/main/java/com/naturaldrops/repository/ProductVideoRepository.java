package com.naturaldrops.repository;

import com.naturaldrops.entity.ProductVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVideoRepository extends JpaRepository<ProductVideo, Long> {
    List<ProductVideo> findByMenuItemIdOrderByIdAsc(Long menuItemId);
    void deleteByMenuItemId(Long menuItemId);
}


