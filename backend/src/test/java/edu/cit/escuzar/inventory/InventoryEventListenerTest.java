package edu.cit.escuzar.inventory;

import edu.cit.escuzar.supplier.SupplierOrderDeliveredEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryEventListenerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final InventoryEventListener listener = new InventoryEventListener(inventoryService);

    @Test
    void restocksInventoryWhenSupplierOrderDeliveredEventReceived() {
        SupplierOrderDeliveredEvent event = new SupplierOrderDeliveredEvent(
                1L,
                "P100",
                24,
                "RO-1",
                "PO-100231"
        );

        listener.onSupplierOrderDelivered(event);

        verify(inventoryService).restock("P100", 24);
    }
}
