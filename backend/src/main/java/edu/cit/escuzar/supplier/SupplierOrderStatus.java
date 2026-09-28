package edu.cit.escuzar.supplier;

/**
 * Public domain enum representing the lifecycle status of a supplier purchase order.
 * Strictly decoupled from any LegacySupply proprietary status codes.
 */
public enum SupplierOrderStatus {
    PENDING,
    ACCEPTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED,
    UNKNOWN
}
