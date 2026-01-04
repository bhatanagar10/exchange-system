package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Transaction implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private String buyOrderId;
    private String sellOrderId;
    private Long buyerId;
    private Long sellerId;
    private double executionPrice;
    private long executionQuantity;
}
