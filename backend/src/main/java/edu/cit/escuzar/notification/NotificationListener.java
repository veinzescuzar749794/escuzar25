package edu.cit.escuzar.notification;

import edu.cit.escuzar.inventory.event.LowStockEvent;
import edu.cit.escuzar.shop.event.OrderPlacedEvent;
import edu.cit.escuzar.shop.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Package-private event listener component.
 * Adheres to the strict modular monolith rule: Notification depends ONLY
 * on domain event classes (OrderPlacedEvent, OrderRejectedEvent, LowStockEvent)
 * and never imports OrderService, InventoryService, or their repositories.
 */
@Component
class NotificationListener {

    private final NotificationRepository notificationRepository;

    NotificationListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
        String msg = "Reorder needed: Low stock for " + event.productId()
                + " (" + event.productName() + ") - remaining: "
                + event.remainingStock() + " units (threshold: " + event.threshold() + ")";
        notificationRepository.save(new Notification(msg, Instant.now()));
    }
}

