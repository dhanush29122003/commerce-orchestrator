
package com.portfolio.commerce_orchestrator.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OutboxClaimingIntegrationTests {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldRecoverEventAfterClaimExpires() {

        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "lease-recovery-test-001",
                "{\"orderId\":3001}"
        );

        event.claim("old-worker", now.minusSeconds(10));

        OutboxEvent savedEvent =
                outboxEventRepository.saveAndFlush(event);

        try {
            List<OutboxEvent> recoveredEvents =
                    transactionTemplate.execute(status ->
                            outboxEventRepository.findClaimableEventsForUpdate(
                                    LocalDateTime.now(),
                                    100
                            )
                    );

            assertThat(recoveredEvents)
                    .extracting(OutboxEvent::getId)
                    .contains(savedEvent.getId());

            OutboxEvent recoveredEvent = recoveredEvents.stream()
                    .filter(item -> item.getId().equals(savedEvent.getId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(recoveredEvent.getClaimedBy())
                    .isEqualTo("old-worker");

            assertThat(recoveredEvent.isClaimExpired(LocalDateTime.now()))
                    .isTrue();

        } finally {
            outboxEventRepository.deleteById(savedEvent.getId());
        }
    }
}
