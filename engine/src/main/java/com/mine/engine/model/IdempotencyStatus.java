package com.mine.engine.model;

/**
 * Status of an idempotency key request
 */
public enum IdempotencyStatus {
    IN_PROGRESS,  // Request is currently being processed
    DONE          // Request has been completed
}
