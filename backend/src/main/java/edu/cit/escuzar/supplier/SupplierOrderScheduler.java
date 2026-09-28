package edu.cit.escuzar.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Package-private background scheduler responsible for:
 * 1. Retrying PENDING purchase orders delayed by supplier outages (Part D)
 * 2. Polling and tracking open purchase orders through to DELIVERED status (Part E)
 */
@Component
class SupplierOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderScheduler.class);

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient httpClient;
    private final ProductSupplierMapping productMapping;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderScheduler(
            SupplierOrderRepository supplierOrderRepository,
            LegacySupplyClient httpClient,
            ProductSupplierMapping productMapping,
            ApplicationEventPublisher eventPublisher
    ) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.httpClient = httpClient;
        this.productMapping = productMapping;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Part D resilience: Resend orders that were queued as PENDING due to a supplier outage.
     * Uses the exact same X-Request-Id and BuyerRef to prevent duplicates.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void retryPendingOrders() {
        List<SupplierOrder> pendingOrders = supplierOrderRepository.findByStatus(SupplierOrderStatus.PENDING);
        if (pendingOrders.isEmpty()) {
            return;
        }

        log.info("Found {} PENDING supplier orders to dispatch/retry", pendingOrders.size());

        for (SupplierOrder order : pendingOrders) {
            try {
                // Safeguard: Check if order was already acknowledged by LegacySupply
                Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> existing =
                        httpClient.findOrderByBuyerRef(order.getBuyerRef());

                if (existing.isPresent()) {
                    LegacySupplyXmlParser.PurchaseOrderStatusDto existingOrder = existing.get();
                    log.info("Pending order {} already acknowledged by LegacySupply as PO={}",
                            order.getId(), existingOrder.poNumber());
                    order.setPoNumber(existingOrder.poNumber());
                    order.setStatus(SupplierGatewayImpl.mapStatusCode(existingOrder.statusCode()));
                    supplierOrderRepository.save(order);
                    continue;
                }

                // Place order using the persisted requestId and buyerRef
                String sku = productMapping.getSupplierSku(order.getProductId());
                LegacySupplyXmlParser.PurchaseOrderAckDto ack = httpClient.placePurchaseOrder(
                        sku,
                        order.getCases(),
                        order.getBuyerRef(),
                        order.getRequestId()
                );

                order.setPoNumber(ack.poNumber());
                order.setStatus(SupplierGatewayImpl.mapStatusCode(ack.statusCode()));
                supplierOrderRepository.save(order);

                log.info("Successfully recovered pending order {}: PO={}, status={}",
                        order.getId(), order.getPoNumber(), order.getStatus());

            } catch (Exception e) {
                log.warn("Retry failed for pending order {} (BuyerRef={}): {}. Will re-attempt on next cycle.",
                        order.getId(), order.getBuyerRef(), e.getMessage());
            }
        }
    }

    /**
     * Part E delivery tracking: Polls status of active orders and triggers restock on delivery.
     * Respects supplier rate limits by using polite polling intervals.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void trackOpenOrders() {
        List<SupplierOrder> openOrders = supplierOrderRepository.findByStatusIn(List.of(
                SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED,
                SupplierOrderStatus.UNKNOWN
        ));

        if (openOrders.isEmpty()) {
            return;
        }

        log.debug("Tracking {} open supplier orders", openOrders.size());

        for (SupplierOrder order : openOrders) {
            if (order.getPoNumber() == null || order.getPoNumber().isBlank()) {
                continue;
            }

            try {
                Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> statusOpt =
                        httpClient.getOrderStatus(order.getPoNumber());

                if (statusOpt.isEmpty()) {
                    continue;
                }

                LegacySupplyXmlParser.PurchaseOrderStatusDto statusDto = statusOpt.get();
                SupplierOrderStatus newStatus = SupplierGatewayImpl.mapStatusCode(statusDto.statusCode());

                if (newStatus != order.getStatus()) {
                    log.info("Supplier order {} (PO={}) transitioned from {} to {}",
                            order.getId(), order.getPoNumber(), order.getStatus(), newStatus);
                    order.setStatus(newStatus);
                    supplierOrderRepository.save(order);

                    if (newStatus == SupplierOrderStatus.DELIVERED) {
                        log.info("Supplier order {} (PO={}) DELIVERED! Publishing domain delivery event for {} units of {}",
                                order.getId(), order.getPoNumber(), order.getUnits(), order.getProductId());

                        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                order.getId(),
                                order.getProductId(),
                                order.getUnits(),
                                order.getBuyerRef(),
                                order.getPoNumber()
                        ));
                    } else if (newStatus == SupplierOrderStatus.CANCELLED) {
                        log.warn("Supplier order {} (PO={}) was CANCELLED by LegacySupply!",
                                order.getId(), order.getPoNumber());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check status for PO={}: {}", order.getPoNumber(), e.getMessage());
            }
        }
    }
}
