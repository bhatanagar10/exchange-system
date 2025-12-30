package com.mine.engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UserEntity - JPA entity for database persistence
 * Represents a user stored in the database
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;
    
    @Column(name = "cash", nullable = false)
    private double cash;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserMarketEntity> userMarkets;
}
