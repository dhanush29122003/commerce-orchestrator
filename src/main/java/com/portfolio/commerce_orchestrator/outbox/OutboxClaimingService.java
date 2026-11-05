
package com.portfolio.commerce_orchestrator.outbox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimingService {

    private final OutboxEventRepository outboxEventRepository;
    private final int batchSize;
    private final long leaseSeconds;

    public OutboxClaimingService(
            OutboxEventRepository outboxEventRepository,
            @Value("${app.outbox.publisher.batch-size:100}") int batchSize,
            @Value("${app.outbox.publisher.lease-seconds:60}") long leaseSeconds) {

        this.outboxEventRepository = outboxEventRepository;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimPendingEvents() {

        LocalDateTime now = LocalDateTime.now();

        String publisherId = UUID.randomUUID().toString();

        List<OutboxEvent> events =
                outboxEventRepository.findClaimableEventsForUpdate(
                        now,
                        batchSize
                );

        LocalDateTime claimExpiry = now.plusSeconds(leaseSeconds);

        List<ClaimedOutboxEvent> claimedEvents = new ArrayList<>();

        for (OutboxEvent event : events) {

            event.claim(publisherId, claimExpiry);

            claimedEvents.add(
                    new ClaimedOutboxEvent(
                            event.getId(),
                            event.getAggregateId(),
                            event.getPayload(),
                            publisherId
                    )
            );
        }

        return claimedEvents;
    }
}
