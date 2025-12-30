package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Transaction implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private long buyOrderId;
    private long sellOrderId;
    private UUID buyerId;
    private UUID sellerId;
    private double executionPrice;
    private long executionQuantity;
}
