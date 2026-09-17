package edu.cit.escuzar.inventory;

import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import edu.cit.escuzar.inventory.event.LowStockEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(inventoryRepository, eventPublisher, 5);
    }

    @Test
    void reservesStockSuccessfullyWithoutLowStockAlertIfAboveThreshold() {
        InventoryItem item = new InventoryItem("P100", "Wireless Mouse", 25);
        when(inventoryRepository.findByProductId("P100")).thenReturn(Optional.of(item));

        ReservationResult result = inventoryService.reserve("P100", 2);

        assertTrue(result.success());
        assertEquals(23, result.item().stock());
        assertEquals(23, item.getStock());
        verify(inventoryRepository).save(item);
        // Stock 23 is >= 5, so no LowStockEvent
        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void publishesLowStockEventWhenStockDropsBelowThreshold() {
        InventoryItem item = new InventoryItem("P200", "Mechanical Keyboard", 10);
        when(inventoryRepository.findByProductId("P200")).thenReturn(Optional.of(item));

        // Reserving 6 leaves remaining stock of 4, which is below threshold 5
        ReservationResult result = inventoryService.reserve("P200", 6);

        assertTrue(result.success());
        assertEquals(4, item.getStock());

        ArgumentCaptor<LowStockEvent> eventCaptor = ArgumentCaptor.forClass(LowStockEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        LowStockEvent event = eventCaptor.getValue();
        assertEquals("P200", event.productId());
        assertEquals(4, event.remainingStock());
        assertEquals(5, event.threshold());
    }

    @Test
    void rejectsReservationWhenQuantityExceedsAvailableStock() {
        InventoryItem item = new InventoryItem("P300", "USB-C Hub", 0);
        when(inventoryRepository.findByProductId("P300")).thenReturn(Optional.of(item));

        ReservationResult result = inventoryService.reserve("P300", 1);

        assertFalse(result.success());
        assertEquals(0, item.getStock());
        verify(inventoryRepository, never()).save(item);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void restocksItemCorrectly() {
        InventoryItem item = new InventoryItem("P100", "Wireless Mouse", 20);
        when(inventoryRepository.findByProductId("P100")).thenReturn(Optional.of(item));

        inventoryService.restock("P100", 5);

        assertEquals(25, item.getStock());
        verify(inventoryRepository).save(item);
    }

    @Test
    void getAllItemsReturnsSortedList() {
        List<InventoryItem> items = List.of(
                new InventoryItem("P100", "Wireless Mouse", 25),
                new InventoryItem("P200", "Mechanical Keyboard", 10)
        );
        when(inventoryRepository.findAll(any(Sort.class))).thenReturn(items);

        List<InventoryItemView> views = inventoryService.getAllItems();

        assertEquals(2, views.size());
        assertEquals("P100", views.get(0).productId());
        assertEquals("P200", views.get(1).productId());
    }
}

