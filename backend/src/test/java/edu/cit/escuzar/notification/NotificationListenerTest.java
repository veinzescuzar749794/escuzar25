package edu.cit.escuzar.notification;

import edu.cit.escuzar.inventory.event.LowStockEvent;
import edu.cit.escuzar.shop.event.OrderItemDto;
import edu.cit.escuzar.shop.event.OrderPlacedEvent;
import edu.cit.escuzar.shop.event.OrderRejectedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationListenerTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationListener notificationListener = new NotificationListener(notificationRepository);

    @Test
    void logsOrderPlacedNotification() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                101L,
                List.of(new OrderItemDto("P100", 2), new OrderItemDto("P200", 1))
        );

        notificationListener.handleOrderPlaced(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertEquals("Order #101 confirmed (2 item(s))", notification.getMessage());
    }

    @Test
    void logsOrderRejectedNotification() {
        OrderRejectedEvent event = new OrderRejectedEvent(
                102L,
                "Requested quantity (5) for P300 exceeds available stock (0)",
                List.of(new OrderItemDto("P300", 5))
        );

        notificationListener.handleOrderRejected(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertEquals("Order #102 rejected: Requested quantity (5) for P300 exceeds available stock (0)", notification.getMessage());
    }

    @Test
    void logsLowStockReorderNeededNotification() {
        LowStockEvent event = new LowStockEvent("P200", "Mechanical Keyboard", 3, 5);

        notificationListener.handleLowStock(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertTrue(notification.getMessage().startsWith("Reorder needed: Low stock for P200"));
        assertTrue(notification.getMessage().contains("remaining: 3 units"));
    }
}

