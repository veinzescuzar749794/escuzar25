package edu.cit.escuzar.supplier;

/**
 * Public domain event published when a supplier order reaches DELIVERED status.
 * Contains only internal domain terms (product ID and units delivered).
 */
public record SupplierOrderDeliveredEvent(
        Long supplierOrderId,
        String productId,
        int unitsDelivered,
        String buyerRef,
        String poNumber
) {
}
