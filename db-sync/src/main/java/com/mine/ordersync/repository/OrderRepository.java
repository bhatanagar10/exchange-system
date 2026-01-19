package com.mine.ordersync.repository;

import com.mine.ordersync.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    
    Optional<OrderEntity> findByOrderId(String orderId);
    
    boolean existsByOrderId(String orderId);
}


