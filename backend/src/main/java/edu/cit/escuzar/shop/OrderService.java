package edu.cit.escuzar.shop;

import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import edu.cit.escuzar.shop.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";

    // Constructor injection against the interface only. This class
    // cannot import InventoryServiceImpl even if it wanted to -- the
    // compiler enforces the module boundary.
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(String productId, int quantity) {
        // In-process call: a direct Java method invocation into the
        // Inventory module, no HTTP, no serialization, same transaction.
        ReservationResult result = inventoryService.reserve(productId, quantity);

        String status = result.success() ? CONFIRMED : REJECTED;

        Order order = new Order(productId, quantity, status, result.reason(), Instant.now());
        orderRepository.save(order);

        return new OrderResponse(status, result.reason(), result.item());
    }
}
