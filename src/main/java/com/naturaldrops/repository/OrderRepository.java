package com.naturaldrops.repository;

import com.naturaldrops.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.buyerId = :buyerId ORDER BY o.orderDate DESC")
    List<Order> findByBuyerIdOrderByOrderDateDesc(@Param("buyerId") Long buyerId);
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status ORDER BY o.orderDate DESC")
    List<Order> findByStatusOrderByOrderDateDesc(@Param("status") Order.OrderStatus status);
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.buyerId = :buyerId AND o.status = :status ORDER BY o.orderDate DESC")
    List<Order> findByBuyerIdAndStatusOrderByOrderDateDesc(@Param("buyerId") Long buyerId, @Param("status") Order.OrderStatus status);
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderDate >= :date ORDER BY o.orderDate DESC")
    List<Order> findByOrderDateAfterOrderByOrderDateDesc(@Param("date") LocalDateTime date);
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderDate >= :startDate AND o.orderDate <= :endDate ORDER BY o.orderDate DESC")
    List<Order> findOrdersBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status AND o.orderDate >= :startDate AND o.orderDate <= :endDate ORDER BY o.orderDate DESC")
    List<Order> findOrdersByStatusBetweenDates(@Param("status") Order.OrderStatus status,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items ORDER BY o.orderDate DESC")
    List<Order> findAllByOrderByOrderDateDesc();
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    java.util.Optional<Order> findByIdWithItems(@Param("id") Long id);
}

