
package com.portfolio.commerce_orchestrator.outbox;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublishingServiceTests {

    private OutboxClaimingService outboxClaimingService;
    private OutboxEventStatusService outboxEventStatusService;
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublishingService outboxPublishingService;

    private ClaimedOutboxEvent claimedEvent;

    @BeforeEach
    void setUp() {

        outboxClaimingService = mock(OutboxClaimingService.class);
        outboxEventStatusService = mock(OutboxEventStatusService.class);
        kafkaTemplate = mock(KafkaTemplate.class);

        outboxPublishingService = new OutboxPublishingService(
                outboxClaimingService,
                outboxEventStatusService,
                kafkaTemplate,
                "order-events"
        );

        claimedEvent = new ClaimedOutboxEvent(
                5001L,
                "order-5001",
                "{\"orderId\":5001}",
                "worker-test-001"
        );
    }

    @Test
    void shouldPublishEventAndMarkItPublished() throws Exception {

        when(outboxClaimingService.claimPendingEvents())
                .thenReturn(List.of(claimedEvent));

        when(kafkaTemplate.send(
                "order-events",
                "order-5001",
                "{\"orderId\":5001}"
        )).thenReturn(CompletableFuture.completedFuture(null));

        when(outboxEventStatusService.markPublished(claimedEvent))
                .thenReturn(true);

        outboxPublishingService.publishPendingEvents();

        verify(kafkaTemplate).send(
                "order-events",
                "order-5001",
                "{\"orderId\":5001}"
        );

        verify(outboxEventStatusService).markPublished(claimedEvent);

        verify(outboxEventStatusService, never())
                .recordFailure(any(), anyString());
    }

    @Test
    void shouldRecordFailureWhenKafkaPublishingFails() throws Exception {

        when(outboxClaimingService.claimPendingEvents())
                .thenReturn(List.of(claimedEvent));

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                "order-events",
                "order-5001",
                "{\"orderId\":5001}"
        )).thenReturn(failedFuture);

        when(outboxEventStatusService.recordFailure(
                claimedEvent,
                "Kafka unavailable"
        )).thenReturn(true);

        outboxPublishingService.publishPendingEvents();

        verify(outboxEventStatusService)
                .recordFailure(claimedEvent, "Kafka unavailable");

        verify(outboxEventStatusService, never())
                .markPublished(any());
    }

    @Test
    void shouldNotOverwriteEventWhenPublisherLosesOwnership()
            throws Exception {

        when(outboxClaimingService.claimPendingEvents())
                .thenReturn(List.of(claimedEvent));

        when(kafkaTemplate.send(
                "order-events",
                "order-5001",
                "{\"orderId\":5001}"
        )).thenReturn(CompletableFuture.completedFuture(null));

        when(outboxEventStatusService.markPublished(claimedEvent))
                .thenReturn(false);

        outboxPublishingService.publishPendingEvents();

        verify(outboxEventStatusService)
                .markPublished(claimedEvent);

        verify(outboxEventStatusService, never())
                .recordFailure(any(), anyString());
    }
}
