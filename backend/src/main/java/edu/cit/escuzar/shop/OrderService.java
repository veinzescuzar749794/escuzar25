package edu.cit.escuzar.shop;

import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.shop.dto.OrderItemOutcome;
import edu.cit.escuzar.shop.dto.OrderItemRequest;
import edu.cit.escuzar.shop.dto.OrderRequest;
import edu.cit.escuzar.shop.dto.OrderResponse;
import edu.cit.escuzar.shop.dto.OrderView;
import edu.cit.escuzar.shop.event.OrderItemDto;
import edu.cit.escuzar.shop.event.OrderPlacedEvent;
import edu.cit.escuzar.shop.event.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    // Constructor injection against the interface only. This class
    // cannot import InventoryServiceImpl even if it wanted to -- the
    // compiler enforces the module boundary.
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderItemRequest> lineItems = request.effectiveItems();

        if (lineItems.isEmpty()) {
            return new OrderResponse(
                    null,
                    REJECTED,
                    "Order must contain at least one item",
                    List.of(),
                    inventoryService.getAllItems()
            );
        }

        // Aggregate requested quantities per product to support multiple line items for the same product
        Map<String, Integer> aggregatedQuantities = new LinkedHashMap<>();
        for (OrderItemRequest item : lineItems) {
            aggregatedQuantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // Step 1: Validate EVERY line item against current stock before reserving anything
        Map<String, InventoryItemView> itemSnapshots = new HashMap<>();
        List<String> rejectionReasons = new ArrayList<>();
        Map<String, String> itemFailureMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : aggregatedQuantities.entrySet()) {
            String productId = entry.getKey();
            int totalRequested = entry.getValue();

            if (totalRequested <= 0) {
                rejectionReasons.add("Quantity for " + productId + " must be greater than zero");
                itemFailureMap.put(productId, "INVALID_QUANTITY");
                continue;
            }

            Optional<InventoryItemView> maybeItem = inventoryService.getItem(productId);
            if (maybeItem.isEmpty()) {
                rejectionReasons.add("Product " + productId + " does not exist");
                itemFailureMap.put(productId, "PRODUCT_NOT_FOUND");
                continue;
            }

            InventoryItemView currentItem = maybeItem.get();
            itemSnapshots.put(productId, currentItem);

            if (totalRequested > currentItem.stock()) {
                rejectionReasons.add("Requested quantity (" + totalRequested + ") for " + productId
                        + " exceeds available stock (" + currentItem.stock() + ")");
                itemFailureMap.put(productId, "INSUFFICIENT_STOCK");
            }
        }

        // Step 2: All-or-nothing check. If any item failed validation, the entire order is REJECTED.
        // No items are reserved -- no partial fulfillment.
        if (!rejectionReasons.isEmpty()) {
            String combinedReason = String.join("; ", rejectionReasons);

            Order order = new Order(REJECTED, combinedReason, Instant.now());
            for (OrderItemRequest itemReq : lineItems) {
                order.addItem(new OrderItem(itemReq.productId(), itemReq.quantity()));
            }
            orderRepository.save(order);

            // Publish OrderRejected event
            List<OrderItemDto> eventItems = lineItems.stream()
                    .map(i -> new OrderItemDto(i.productId(), i.quantity()))
                    .toList();
            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), combinedReason, eventItems));

            List<OrderItemOutcome> outcomes = lineItems.stream()
                    .map(i -> new OrderItemOutcome(
                            i.productId(),
                            itemFailureMap.getOrDefault(i.productId(), "REJECTED_DUE_TO_ORDER_FAILURE")
                    ))
                    .toList();

            return new OrderResponse(
                    order.getOrderId(),
                    REJECTED,
                    combinedReason,
                    outcomes,
                    inventoryService.getAllItems()
            );
        }

        // Step 3: All items passed validation. Create order and call InventoryService.reserve() for each item.
        Order order = new Order(CONFIRMED, null, Instant.now());
        for (OrderItemRequest itemReq : lineItems) {
            order.addItem(new OrderItem(itemReq.productId(), itemReq.quantity()));
        }
        orderRepository.save(order);

        List<OrderItemOutcome> outcomes = new ArrayList<>();
        for (OrderItemRequest itemReq : lineItems) {
            inventoryService.reserve(itemReq.productId(), itemReq.quantity());
            outcomes.add(new OrderItemOutcome(itemReq.productId(), "RESERVED"));
        }

        // Publish OrderPlaced event
        List<OrderItemDto> eventItems = lineItems.stream()
                .map(i -> new OrderItemDto(i.productId(), i.quantity()))
                .toList();
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), eventItems));

        return new OrderResponse(
                order.getOrderId(),
                CONFIRMED,
                null,
                outcomes,
                inventoryService.getAllItems()
        );
    }

    @Transactional
    public OrderView cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order #" + orderId + " not found"));

        if (CANCELLED.equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order #" + orderId + " is already CANCELLED");
        }

        if (REJECTED.equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order #" + orderId + " was REJECTED and cannot be cancelled");
        }

        // Return every reserved line item's quantity back to stock
        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(CANCELLED);
        orderRepository.save(order);

        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toView)
                .toList();
    }

    private OrderView toView(Order order) {
        List<OrderView.OrderItemView> itemViews = order.getItems()
                .stream()
                .map(item -> new OrderView.OrderItemView(item.getProductId(), item.getQuantity()))
                .toList();

        return new OrderView(
                order.getOrderId(),
                order.getStatus(),
                order.getReason(),
                order.getCreatedAt(),
                itemViews
        );
    }
}
