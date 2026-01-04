package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id; // Format: ORD-{userId}-{timestamp}
    private Long userId;
    private OrderType type;
    private OrderExecutionType executionType;
    private double price;
    private long quantity;
    private long originalQuantity;
    private Market market;

    public Order(String id, Long userId, OrderType type, OrderExecutionType executionType,
                 double price, long quantity, Market market) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.executionType = executionType;
        this.price = price;
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.market = market;
    }

}
