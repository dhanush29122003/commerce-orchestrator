
package com.portfolio.commerce_orchestrator.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OutboxClaimingServiceTests {

    @Autowired
    private OutboxClaimingService outboxClaimingService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldReclaimExpiredEventWithNewPublisherId() {

        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "reclaim-test-001",
                "{\"orderId\":4001}"
        );

        event.claim("old-worker", now.minusSeconds(10));

        OutboxEvent savedEvent =
                outboxEventRepository.saveAndFlush(event);

        try {
            List<ClaimedOutboxEvent> claimedEvents =
                    outboxClaimingService.claimPendingEvents();

            ClaimedOutboxEvent reclaimedEvent = claimedEvents.stream()
                    .filter(item -> item.id().equals(savedEvent.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expired event was not reclaimed"
                    ));

            assertThat(reclaimedEvent.publisherId())
                    .isNotEqualTo("old-worker");

            assertThat(reclaimedEvent.aggregateId())
                    .isEqualTo("reclaim-test-001");

            OutboxEvent updatedEvent =
                    outboxEventRepository.findById(savedEvent.getId())
                            .orElseThrow();

            assertThat(updatedEvent.getClaimedBy())
                    .isEqualTo(reclaimedEvent.publisherId());

            assertThat(updatedEvent.getClaimedUntil())
                    .isAfter(LocalDateTime.now());

            assertThat(updatedEvent.getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

        } finally {
            outboxEventRepository.deleteById(savedEvent.getId());
        }
    }
}
