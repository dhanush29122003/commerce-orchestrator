
package com.portfolio.commerce_orchestrator.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private final OutboxPublishingService outboxPublishingService;

    public OutboxPublisher(
            OutboxPublishingService outboxPublishingService) {
        this.outboxPublishingService = outboxPublishingService;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.fixed-delay:5000}")
    public void publishPendingEvents() {
        outboxPublishingService.publishPendingEvents();
    }
}
