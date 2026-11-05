
package com.portfolio.commerce_orchestrator;

import com.portfolio.commerce_orchestrator.idempotency.IdempotencyRecord;
import com.portfolio.commerce_orchestrator.idempotency.IdempotencyRepository;
import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.order.OrderStatus;
import com.portfolio.commerce_orchestrator.outbox.OutboxEvent;
import com.portfolio.commerce_orchestrator.outbox.OutboxEventRepository;
import com.portfolio.commerce_orchestrator.service.IdempotencyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdempotencyServiceTests {

    private IdempotencyRepository idempotencyRepository;
    private OrderRepository orderRepository;
    private OutboxEventRepository outboxEventRepository;

    private IdempotencyService idempotencyService;

    private final Map<String, IdempotencyRecord> records =
            new HashMap<>();

    private final AtomicLong orderIdSequence =
            new AtomicLong(100);

    @BeforeEach
    void setUp() {

        records.clear();
        orderIdSequence.set(100);

        idempotencyRepository = mock(IdempotencyRepository.class);
        orderRepository = mock(OrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);

        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));

        when(idempotencyRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(
                                records.get(invocation.getArgument(0))
                        )
                );

        when(idempotencyRepository.saveAndFlush(
                any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> {

                    IdempotencyRecord record =
                            invocation.getArgument(0);

                    records.put(
                            record.getIdempotencyKey(),
                            record
                    );

                    return record;
                });

        // Simulate MySQL generating an ID for a saved order.
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {

                    Order order = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            order,
                            "id",
                            orderIdSequence.getAndIncrement()
                    );

                    return order;
                });

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        idempotencyService = new IdempotencyService(
                idempotencyRepository,
                orderRepository,
                outboxEventRepository,
                new ObjectMapper(),
                transactionManager
        );
    }

    @Test
    void shouldCreateNewOrderAndOutboxEvent() {

        Order order = idempotencyService.createOrder(
                "test-key-1",
                "Headphones",
                2
        );

        assertNotNull(order.getId());
        assertEquals("Headphones", order.getProductName());
        assertEquals(2, order.getQuantity());
        assertEquals(OrderStatus.PENDING, order.getStatus());

        verify(orderRepository, times(1))
                .save(any(Order.class));

        verify(idempotencyRepository, times(1))
                .saveAndFlush(any(IdempotencyRecord.class));

        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldReturnExistingOrderWithoutCreatingAnotherOutboxEvent() {

        Order firstOrder = idempotencyService.createOrder(
                "test-key-2",
                "Keyboard",
                1
        );

        Order retryOrder = idempotencyService.createOrder(
                "test-key-2",
                "Keyboard",
                1
        );

        assertEquals(
                firstOrder.getId(),
                retryOrder.getId()
        );

        assertEquals(
                firstOrder.getProductName(),
                retryOrder.getProductName()
        );

        assertEquals(
                firstOrder.getQuantity(),
                retryOrder.getQuantity()
        );

        verify(orderRepository, times(1))
                .save(any(Order.class));

        verify(idempotencyRepository, times(1))
                .saveAndFlush(any(IdempotencyRecord.class));

        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectDifferentPayloadForSameKey() {

        idempotencyService.createOrder(
                "test-key-3",
                "Headphones",
                2
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> idempotencyService.createOrder(
                        "test-key-3",
                        "Monitor",
                        1
                )
        );

        assertEquals(
                409,
                exception.getStatusCode().value()
        );

        verify(orderRepository, times(1))
                .save(any(Order.class));

        verify(idempotencyRepository, times(1))
                .saveAndFlush(any(IdempotencyRecord.class));

        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
    }
}
