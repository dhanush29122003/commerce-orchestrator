
package com.portfolio.commerce_orchestrator.outbox;

public record ClaimedOutboxEvent(
        Long id,
        String aggregateId,
        String payload,
        String publisherId
) {
}
