package edu.cit.escuzar.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Package-private implementation of the SupplierGateway anti-corruption layer.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final ProductSupplierMapping productMapping;
    private final LegacySupplyClient httpClient;
    private final SupplierOrderRepository supplierOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    SupplierGatewayImpl(
            ProductSupplierMapping productMapping,
            LegacySupplyClient httpClient,
            SupplierOrderRepository supplierOrderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productMapping = productMapping;
        this.httpClient = httpClient;
        this.supplierOrderRepository = supplierOrderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenOrderForProduct(String productId) {
        return supplierOrderRepository.existsByProductIdAndStatusIn(productId, java.util.List.of(
                SupplierOrderStatus.PENDING, SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED));
    }

    @Override
    public String getSupplierSku(String productId) { return productMapping.getSupplierSku(productId); }

    @Override
    @Transactional
    public SupplierOrderResult placeOrder(String productId, int unitsNeeded) {
        if (!productMapping.supportsProduct(productId)) {
            throw new IllegalArgumentException("Unsupported product ID for supplier reorder: " + productId);
        }

        int cases = productMapping.calculateCases(productId, unitsNeeded);
        int totalUnits = productMapping.calculateDeliveredUnits(productId, cases);
        String sku = productMapping.getSupplierSku(productId);
        String requestId = UUID.randomUUID().toString();

        // 1. Create and persist order as PENDING before attempting any network call
        SupplierOrder order = new SupplierOrder(
                productId,
                "RO-TMP-" + UUID.randomUUID(),
                requestId,
                cases,
                totalUnits,
                SupplierOrderStatus.PENDING
        );
        order = supplierOrderRepository.saveAndFlush(order);

        // Assign deterministic BuyerRef based on generated primary key
        order.setBuyerRef("RO-" + order.getId());
        order = supplierOrderRepository.saveAndFlush(order);

        // 2. Dispatch to LegacySupply with retry and idempotency
        try {
            LegacySupplyXmlParser.PurchaseOrderAckDto ack = httpClient.placePurchaseOrder(
                    sku,
                    cases,
                    order.getBuyerRef(),
                    order.getRequestId()
            );

            order.setPoNumber(ack.poNumber());
            order.setStatus(mapStatusCode(ack.statusCode()));
            order = supplierOrderRepository.save(order);
            publishUnknownStatusIfNeeded(order, ack.statusCode());

            log.info("Supplier order {} placed with LegacySupply: PO={}, status={}",
                    order.getId(), order.getPoNumber(), order.getStatus());

            return new SupplierOrderResult(
                    order.getId(),
                    productId,
                    order.getBuyerRef(),
                    cases,
                    totalUnits,
                    order.getStatus(),
                    order.getPoNumber(),
                    "Order placed successfully with PO " + order.getPoNumber()
            );

        } catch (Exception e) {
            log.warn("Immediate order placement failed for order {} (BuyerRef={}): {}. Saved as PENDING for scheduled retry.",
                    order.getId(), order.getBuyerRef(), e.getMessage());

            return new SupplierOrderResult(
                    order.getId(),
                    productId,
                    order.getBuyerRef(),
                    cases,
                    totalUnits,
                    SupplierOrderStatus.PENDING,
                    null,
                    "Order queued as PENDING due to supplier unavailability: " + e.getMessage()
            );
        }
    }

    public static SupplierOrderStatus mapStatusCode(int statusCode) {
        return switch (statusCode) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            case 50, 90 -> SupplierOrderStatus.CANCELLED;
            default -> {
                log.warn("Received unexpected supplier status code: {}. Mapping to UNKNOWN.", statusCode);
                yield SupplierOrderStatus.UNKNOWN;
            }
        };
    }

    void publishUnknownStatusIfNeeded(SupplierOrder order, int rawStatusCode) {
        if (order.getStatus() == SupplierOrderStatus.UNKNOWN) {
            eventPublisher.publishEvent(new SupplierOrderUnknownStatusEvent(order.getId(), order.getProductId(),
                    order.getBuyerRef(), order.getPoNumber(), rawStatusCode));
        }
    }
}
