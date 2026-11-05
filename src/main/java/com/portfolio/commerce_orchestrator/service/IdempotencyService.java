
package com.portfolio.commerce_orchestrator.service;

import com.portfolio.commerce_orchestrator.events.OrderCreatedEvent;
import com.portfolio.commerce_orchestrator.idempotency.IdempotencyRecord;
import com.portfolio.commerce_orchestrator.idempotency.IdempotencyRepository;
import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.outbox.OutboxEvent;
import com.portfolio.commerce_orchestrator.outbox.OutboxEventRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(
            IdempotencyRepository idempotencyRepository,
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {

        this.idempotencyRepository = idempotencyRepository;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;

        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public Order createOrder(
            String key,
            String productName,
            int quantity) {

        String fingerprint = createFingerprint(productName, quantity);

        try {
            CreateOrderResult result = transactionTemplate.execute(status -> {

                var existing =
                        idempotencyRepository.findByIdempotencyKey(key);

                // Return the original order for an idempotent replay.
                if (existing.isPresent()) {
                    IdempotencyRecord record = existing.get();

                    validateFingerprint(
                            record.getRequestFingerprint(),
                            fingerprint
                    );

                    return new CreateOrderResult(
                            deserializeOrder(record.getResponseBody()),
                            false
                    );
                }

                // 1. Save the new order.
                Order order = orderRepository.save(
                        new Order(productName, quantity)
                );

                // 2. Save the idempotency record.
                String responseBody = serializeOrder(order);

                try {
                    idempotencyRepository.saveAndFlush(
                            new IdempotencyRecord(
                                    key,
                                    fingerprint,
                                    responseBody
                            )
                    );
                } catch (DataIntegrityViolationException exception) {
                    throw new IdempotencyRecordInsertException(exception);
                }

                // 3. Create the order-created event.
                OrderCreatedEvent event = new OrderCreatedEvent(
                        order.getId(),
                        order.getProductName(),
                        order.getQuantity(),
                        order.getStatus().name(),
                        order.getCreatedAt().format(
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        )
                );

                // 4. Serialize the event.
                String eventPayload = serializeEvent(event);

                // 5. Save the event in the outbox table.
                OutboxEvent outboxEvent = new OutboxEvent(
                        "ORDER_CREATED",
                        "ORDER",
                        order.getId().toString(),
                        eventPayload
                );

                outboxEventRepository.save(outboxEvent);

                return new CreateOrderResult(order, true);
            });

            if (result == null) {
                throw new IllegalStateException(
                        "Order creation transaction returned no result."
                );
            }

            return result.order();

        } catch (IdempotencyRecordInsertException exception) {

            DataIntegrityViolationException originalException =
                    (DataIntegrityViolationException) exception.getCause();

            try {
                return readExistingOrder(key, fingerprint);

            } catch (ResponseStatusException lookupException) {

                boolean recordWasNotFoundAfterConflict =
                        lookupException.getStatusCode().value() == 409
                        && lookupException.getReason() != null
                        && lookupException.getReason().startsWith(
                                "The request conflicted with another database operation"
                        );

                if (recordWasNotFoundAfterConflict) {
                    throw originalException;
                }

                throw lookupException;
            }
        }
    }

    private Order readExistingOrder(
            String key,
            String fingerprint) {

        return transactionTemplate.execute(status -> {

            IdempotencyRecord record = idempotencyRepository
                    .findByIdempotencyKey(key)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The request conflicted with another database operation. Please retry."
                    ));

            validateFingerprint(
                    record.getRequestFingerprint(),
                    fingerprint
            );

            return deserializeOrder(record.getResponseBody());
        });
    }

    private void validateFingerprint(
            String savedFingerprint,
            String requestedFingerprint) {

        if (savedFingerprint == null || savedFingerprint.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This key belongs to a legacy request. Use a new idempotency key."
            );
        }

        if (!savedFingerprint.equals(requestedFingerprint)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This idempotency key was already used with a different request."
            );
        }
    }

    private String serializeOrder(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize the order response.",
                    exception
            );
        }
    }

    private String serializeEvent(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize the order-created event.",
                    exception
            );
        }
    }

    private Order deserializeOrder(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, Order.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not read the stored order response.",
                    exception
            );
        }
    }

    private String createFingerprint(
            String productName,
            int quantity) {

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (productName + "\n" + quantity)
                            .getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }

    private record CreateOrderResult(Order order, boolean created) {
    }

    private static class IdempotencyRecordInsertException
            extends RuntimeException {

        IdempotencyRecordInsertException(
                DataIntegrityViolationException cause) {
            super(cause);
        }
    }
}
