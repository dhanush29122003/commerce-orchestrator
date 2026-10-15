
package com.portfolio.commerce_orchestrator;

import com.portfolio.commerce_orchestrator.idempotency.IdempotencyRepository;
import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.service.IdempotencyService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyIntegrationTests {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Test
    void shouldPersistOrderAndIdempotencyRecordInDatabase() {

        String key = "integration-" + UUID.randomUUID();

        Order firstOrder = idempotencyService.createOrder(
                key,
                "Integration Headphones",
                2
        );

        Assertions.assertNotNull(firstOrder.getId());

        Assertions.assertTrue(
                orderRepository.findById(firstOrder.getId()).isPresent()
        );

        var record = idempotencyRepository.findByIdempotencyKey(key);

        Assertions.assertTrue(record.isPresent());

        Order retryOrder = idempotencyService.createOrder(
                key,
                "Integration Headphones",
                2
        );

        Assertions.assertEquals(
                firstOrder.getId(),
                retryOrder.getId()
        );

        Assertions.assertEquals(
                1,
                orderRepository.findAll().stream()
                        .filter(order ->
                                firstOrder.getId().equals(order.getId()))
                        .count()
        );
    }

    @Test
    void shouldCreateOnlyOneOrderForConcurrentRequests() throws Exception {

        String key = "concurrent-" + UUID.randomUUID();

        long ordersBefore = orderRepository.count();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Order> firstRequest = executor.submit(() -> {
                ready.countDown();
                start.await();

                return idempotencyService.createOrder(
                        key,
                        "Gaming Mouse",
                        1
                );
            });

            Future<Order> secondRequest = executor.submit(() -> {
                ready.countDown();
                start.await();

                return idempotencyService.createOrder(
                        key,
                        "Gaming Mouse",
                        1
                );
            });

            // Wait until both requests are ready.
            Assertions.assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both requests should be ready"
            );

            // Allow both requests to start.
            start.countDown();

            Order order1 = firstRequest.get(15, TimeUnit.SECONDS);
            Order order2 = secondRequest.get(15, TimeUnit.SECONDS);

            // Both requests must return the same order.
            Assertions.assertEquals(
                    order1.getId(),
                    order2.getId(),
                    "Both requests should return the same order ID"
            );

            // Only one new order should have been created.
            Assertions.assertEquals(
                    ordersBefore + 1,
                    orderRepository.count(),
                    "Concurrent retries must not create duplicate orders"
            );

            // The idempotency record must be persisted.
            Assertions.assertTrue(
                    idempotencyRepository
                            .findByIdempotencyKey(key)
                            .isPresent(),
                    "The idempotency record should exist"
            );

        } finally {
            executor.shutdownNow();
        }
    }
}
