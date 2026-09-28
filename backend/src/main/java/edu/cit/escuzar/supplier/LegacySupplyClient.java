package edu.cit.escuzar.supplier;

import java.util.Optional;

/**
 * Package-private interface defining HTTP operations with LegacySupply.
 */
interface LegacySupplyClient {

    LegacySupplyXmlParser.PurchaseOrderAckDto placePurchaseOrder(
            String supplierSku,
            int cases,
            String buyerRef,
            String requestId
    );

    Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> getOrderStatus(String poNumber);

    Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> findOrderByBuyerRef(String buyerRef);
}
