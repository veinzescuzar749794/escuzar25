package edu.cit.escuzar.supplier;

/** Domain event emitted when LegacySupply reports a status code this adapter does not recognize. */
public record SupplierOrderUnknownStatusEvent(
        Long supplierOrderId,
        String productId,
        String buyerRef,
        String poNumber,
        int rawStatusCode
) {}
