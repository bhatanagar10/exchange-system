package com.mine.engine.entity;

import com.mine.engine.model.Market;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UserMarketEntity - JPA entity for user market holdings
 * Represents a user's stock quantity for a specific market
 */
@Entity
@Table(name = "user_market", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "market"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMarketEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false, length = 50)
    private Market market;
    
    @Column(name = "stocks", nullable = false)
    private Long stocks;

}
