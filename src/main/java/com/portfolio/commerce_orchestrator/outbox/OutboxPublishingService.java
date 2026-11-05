
package com.portfolio.commerce_orchestrator.outbox;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublishingService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublishingService.class);

    private final OutboxClaimingService outboxClaimingService;
    private final OutboxEventStatusService outboxEventStatusService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxPublishingService(
            OutboxClaimingService outboxClaimingService,
            OutboxEventStatusService outboxEventStatusService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.order-events:order-events}") String topic) {

        this.outboxClaimingService = outboxClaimingService;
        this.outboxEventStatusService = outboxEventStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishPendingEvents() {

        List<ClaimedOutboxEvent> claimedEvents =
                outboxClaimingService.claimPendingEvents();

        for (ClaimedOutboxEvent event : claimedEvents) {

            try {
                kafkaTemplate.send(
                        topic,
                        event.aggregateId(),
                        event.payload()
                ).get();

                boolean published =
                        outboxEventStatusService.markPublished(event);

                if (published) {
                    log.info(
                            "Published outbox event. Event ID: {}, Aggregate ID: {}",
                            event.id(),
                            event.aggregateId()
                    );
                } else {
                    log.warn(
                            "Could not mark event as published because claim ownership expired or changed. Event ID: {}",
                            event.id()
                    );
                }

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();

                boolean recorded = outboxEventStatusService.recordFailure(
                        event,
                        "Publishing interrupted"
                );

                log.warn(
                        "Publishing interrupted for event ID: {}. Failure recorded: {}",
                        event.id(),
                        recorded
                );

                break;

            } catch (ExecutionException | RuntimeException exception) {

                Throwable cause = exception.getCause();

                String errorMessage = cause != null
                        ? cause.getMessage()
                        : exception.getMessage();

                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = "Unknown Kafka publishing error";
                }

                boolean recorded = outboxEventStatusService.recordFailure(
                        event,
                        errorMessage
                );

                log.error(
                        "Failed to publish event ID: {}. Failure recorded: {}",
                        event.id(),
                        recorded,
                        exception
                );
            }
        }
    }
}
