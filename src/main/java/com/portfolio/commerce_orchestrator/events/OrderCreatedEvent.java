package com.portfolio.commerce_orchestrator.events;

public record OrderCreatedEvent(
        Long orderId,
        String productName,
        int quantity,
        String status,
        String createdAt
) {
}