package com.mine.ordersync.repository;

import com.mine.ordersync.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    
    Optional<TransactionEntity> findByTransactionId(Long transactionId);
    
    boolean existsByTransactionId(Long transactionId);
}


