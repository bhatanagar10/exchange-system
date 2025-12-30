package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Comparator;
import java.util.PriorityQueue;

@Getter
@Setter
@AllArgsConstructor
public class OrderBook implements Serializable {
    private static final long serialVersionUID = 1L;
    // Buy orders: Max heap (highest price first) - descending order
    private PriorityQueue<Order> buyOrders;
    // Sell orders: Min heap (lowest price first) - ascending order
    private PriorityQueue<Order> sellOrders;
    
    public OrderBook() {
        // Buy orders: Max heap - highest price has highest priority
        this.buyOrders = new PriorityQueue<>(Comparator
                .comparing(Order::getPrice)
                .reversed()
                .thenComparing(Order::getId)); // If same price, earlier order first
        
        // Sell orders: Min heap - lowest price has highest priority
        this.sellOrders = new PriorityQueue<>(Comparator
                .comparing(Order::getPrice)
                .thenComparing(Order::getId)); // If same price, earlier order first
    }
}
