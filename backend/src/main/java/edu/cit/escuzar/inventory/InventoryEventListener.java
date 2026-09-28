package edu.cit.escuzar.inventory;

import edu.cit.escuzar.supplier.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Package-private event listener in Inventory module.
 * Listens for SupplierOrderDeliveredEvent and restocks inventory with the delivered units.
 * Never imports anything describing LegacySupply, upholding strict architectural boundaries.
 */
@Component
class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;

    InventoryEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        log.info("Inventory received SupplierOrderDeliveredEvent: Restocking {} units of product {}",
                event.unitsDelivered(), event.productId());
        inventoryService.restock(event.productId(), event.unitsDelivered());
    }
}
