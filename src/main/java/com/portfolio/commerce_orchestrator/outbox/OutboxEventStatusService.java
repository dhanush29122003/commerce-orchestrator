
package com.portfolio.commerce_orchestrator.outbox;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventStatusService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventStatusService(
            OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(ClaimedOutboxEvent event) {

        LocalDateTime now = LocalDateTime.now();

        int updated = outboxEventRepository.markPublishedIfOwned(
                event.id(),
                event.publisherId(),
                OutboxStatus.PENDING,
                OutboxStatus.PUBLISHED,
                now,
                now
        );

        return updated == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(
            ClaimedOutboxEvent event,
            String errorMessage) {

        int updated = outboxEventRepository.recordFailureIfOwned(
                event.id(),
                event.publisherId(),
                OutboxStatus.PENDING,
                errorMessage,
                LocalDateTime.now()
        );

        return updated == 1;
    }
}
