package edu.cit.escuzar.shop;

import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import edu.cit.escuzar.shop.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService orderService = new OrderService(inventoryService, orderRepository);

    @Test
    void confirmsOrderAndPersistsReservationResult() {
        InventoryItemView item = new InventoryItemView("P100", "Wireless Mouse", 24);
        when(inventoryService.reserve("P100", 1))
                .thenReturn(new ReservationResult(true, null, item));

        OrderResponse response = orderService.placeOrder("P100", 1);

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(24, response.inventory().stock());

        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertEquals("CONFIRMED", order.getValue().getStatus());
        assertNull(order.getValue().getReason());
        verify(inventoryService).reserve("P100", 1);
    }

    @Test
    void rejectsOrderAndPersistsRejectionReason() {
        InventoryItemView item = new InventoryItemView("P300", "USB-C Hub", 0);
        String reason = "Requested quantity (1) exceeds available stock (0)";
        when(inventoryService.reserve("P300", 1))
                .thenReturn(new ReservationResult(false, reason, item));

        OrderResponse response = orderService.placeOrder("P300", 1);

        assertEquals("REJECTED", response.status());
        assertEquals(reason, response.reason());
        assertEquals(0, response.inventory().stock());

        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertEquals("REJECTED", order.getValue().getStatus());
        assertEquals(reason, order.getValue().getReason());
        verify(inventoryService).reserve("P300", 1);
    }
}
