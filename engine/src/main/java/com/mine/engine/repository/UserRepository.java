package com.mine.engine.repository;

import com.mine.engine.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for UserEntity
 * Provides CRUD operations and custom queries
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    
    /**
     * Check if user exists by ID
     * 
     * @param id The Long ID of the user
     * @return true if user exists, false otherwise
     */
    boolean existsById(Long id);
    
    /**
     * Find user by ID with user markets eagerly loaded
     * 
     * @param id The Long ID of the user
     * @return Optional UserEntity with user markets loaded
     */
    @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.userMarkets WHERE u.id = :id")
    Optional<UserEntity> findByIdWithMarkets(@Param("id") Long id);
}
