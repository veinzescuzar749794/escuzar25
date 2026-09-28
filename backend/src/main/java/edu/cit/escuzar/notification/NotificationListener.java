package edu.cit.escuzar.notification;

import edu.cit.escuzar.inventory.event.LowStockEvent;
import edu.cit.escuzar.shop.event.OrderPlacedEvent;
import edu.cit.escuzar.shop.event.OrderRejectedEvent;
import edu.cit.escuzar.supplier.SupplierGateway;
import edu.cit.escuzar.supplier.SupplierOrderDeliveredEvent;
import edu.cit.escuzar.supplier.SupplierOrderResult;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Package-private event listener component.
 * Handles domain events across the system:
 * - Order placed / rejected notifications
 * - Low-stock auto-reorder rule (dispatches purchase order via SupplierGateway)
 * - Supplier order delivery notifications
 */
@Component
class NotificationListener {

    private final NotificationRepository notificationRepository;
    private final SupplierGateway supplierGateway;

    NotificationListener(NotificationRepository notificationRepository, SupplierGateway supplierGateway) {
        this.notificationRepository = notificationRepository;
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String msg = "Order #" + event.orderId() + " confirmed (" + event.items().size() + " item(s))";
        notificationRepository.save(new Notification(msg, Instant.now()));
    }

    @EventListener
    public void handleOrderRejected(OrderRejectedEvent event) {
        String msg = "Order #" + event.orderId() + " rejected: " + event.reason();
        notificationRepository.save(new Notification(msg, Instant.now()));
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        // Auto-reorder rule: calculate units needed to restore stock comfortably above threshold
        int unitsNeeded = Math.max(1, (event.threshold() * 4) - event.remainingStock());
        SupplierOrderResult result = supplierGateway.placeOrder(event.productId(), unitsNeeded);

        String poInfo = result.poNumber() != null ? ", PO: " + result.poNumber() : "";
        String msg = "Auto-reorder placed for " + event.productId() + " (" + event.productName() + "): "
                + result.cases() + " case(s) (" + result.units() + " units), Status: " + result.status() + poInfo;

        notificationRepository.save(new Notification(msg, Instant.now()));
    }

    @EventListener
    public void handleSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        String msg = "Supplier order " + event.poNumber() + " delivered: Restocked "
                + event.unitsDelivered() + " units for product " + event.productId();
        notificationRepository.save(new Notification(msg, Instant.now()));
    }
}
