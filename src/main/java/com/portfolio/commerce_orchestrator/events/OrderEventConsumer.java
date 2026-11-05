
package com.portfolio.commerce_orchestrator.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.topics.order-events:order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderCreatedEvent(String message) {
        log.info("Received order event from Kafka: {}", message);
    }
}
