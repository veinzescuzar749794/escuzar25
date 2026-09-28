package edu.cit.escuzar.supplier;

/**
 * Public domain record representing the result of a supplier purchase order request.
 * Contains purely internal domain terms.
 */
public record SupplierOrderResult(
        Long orderId,
        String productId,
        String buyerRef,
        int cases,
        int units,
        SupplierOrderStatus status,
        String poNumber,
        String message
) {
}
