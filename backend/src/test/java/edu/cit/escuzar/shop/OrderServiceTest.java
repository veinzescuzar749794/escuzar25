package edu.cit.escuzar.shop;

import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import edu.cit.escuzar.shop.dto.OrderItemRequest;
import edu.cit.escuzar.shop.dto.OrderRequest;
import edu.cit.escuzar.shop.dto.OrderResponse;
import edu.cit.escuzar.shop.dto.OrderView;
import edu.cit.escuzar.shop.event.OrderPlacedEvent;
import edu.cit.escuzar.shop.event.OrderRejectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderService orderService = new OrderService(inventoryService, orderRepository, eventPublisher);

    @BeforeEach
    void setUp() {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItemView("P100", "Wireless Mouse", 25),
                new InventoryItemView("P200", "Mechanical Keyboard", 10),
                new InventoryItemView("P300", "USB-C Hub", 0)
        ));
    }

    @Test
    void confirmsMultiItemOrderAndReservesAllItems() {
        when(inventoryService.getItem("P100"))
                .thenReturn(Optional.of(new InventoryItemView("P100", "Wireless Mouse", 25)));
        when(inventoryService.getItem("P200"))
                .thenReturn(Optional.of(new InventoryItemView("P200", "Mechanical Keyboard", 10)));

        when(inventoryService.reserve("P100", 2))
                .thenReturn(new ReservationResult(true, null, new InventoryItemView("P100", "Wireless Mouse", 23)));
        when(inventoryService.reserve("P200", 1))
                .thenReturn(new ReservationResult(true, null, new InventoryItemView("P200", "Mechanical Keyboard", 9)));

        OrderRequest request = new OrderRequest(
                List.of(
                        new OrderItemRequest("P100", 2),
                        new OrderItemRequest("P200", 1)
                ),
                null,
                null
        );

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(2, response.items().size());
        assertEquals("RESERVED", response.items().get(0).outcome());
        assertEquals("RESERVED", response.items().get(1).outcome());

        verify(inventoryService).reserve("P100", 2);
        verify(inventoryService).reserve("P200", 1);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals("CONFIRMED", orderCaptor.getValue().getStatus());
        assertEquals(2, orderCaptor.getValue().getItems().size());

        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsMultiItemOrderWithAllOrNothingRollbackWhenOneItemExceedsStock() {
        // P100 has 25 available, but P300 has 0 available
        when(inventoryService.getItem("P100"))
                .thenReturn(Optional.of(new InventoryItemView("P100", "Wireless Mouse", 25)));
        when(inventoryService.getItem("P300"))
                .thenReturn(Optional.of(new InventoryItemView("P300", "USB-C Hub", 0)));

        OrderRequest request = new OrderRequest(
                List.of(
                        new OrderItemRequest("P100", 2),
                        new OrderItemRequest("P300", 1)
                ),
                null,
                null
        );

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("REJECTED", response.status());
        assertNotNull(response.reason());
        assertTrue(response.reason().contains("exceeds available stock"));

        // Crucial all-or-nothing requirement: NO items are reserved!
        verify(inventoryService, never()).reserve(any(), any(Integer.class));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals("REJECTED", orderCaptor.getValue().getStatus());

        verify(eventPublisher).publishEvent(any(OrderRejectedEvent.class));
    }

    @Test
    void cancelsConfirmedOrderAndRestocksAllLineItems() {
        Order confirmedOrder = new Order("CONFIRMED", null, Instant.now());
        confirmedOrder.addItem(new OrderItem("P100", 2));
        confirmedOrder.addItem(new OrderItem("P200", 1));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(confirmedOrder));

        OrderView view = orderService.cancelOrder(1L);

        assertEquals("CANCELLED", view.status());
        verify(inventoryService).restock("P100", 2);
        verify(inventoryService).restock("P200", 1);
        verify(orderRepository).save(confirmedOrder);
    }

    @Test
    void throwsNotFoundWhenCancellingNonExistentOrder() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(99L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void throwsConflictWhenCancellingAlreadyCancelledOrder() {
        Order cancelledOrder = new Order("CANCELLED", null, Instant.now());
        when(orderRepository.findById(2L)).thenReturn(Optional.of(cancelledOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(2L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(inventoryService, never()).restock(any(), any(Integer.class));
    }
}
