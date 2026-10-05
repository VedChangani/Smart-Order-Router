package com.sor.common.model;

/**
 * Final disposition of an order after the Smart Order Router has attempted
 * execution across the available exchanges.
 */
public enum OrderStatus {
    FILLED,
    PARTIALLY_FILLED,
    REJECTED
}
