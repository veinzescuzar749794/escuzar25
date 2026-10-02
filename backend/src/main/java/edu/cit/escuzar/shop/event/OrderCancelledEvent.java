package edu.cit.escuzar.shop.event;

import java.util.List;

public record OrderCancelledEvent(Long orderId, List<OrderItemDto> items) {}
