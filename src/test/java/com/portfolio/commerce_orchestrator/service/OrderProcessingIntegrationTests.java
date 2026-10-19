
package com.portfolio.commerce_orchestrator.service;

import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.order.OrderStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
class OrderProcessingIntegrationTests {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderProcessingService orderProcessingService;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void successfulPaymentPersistsCompletedOrderStatus() {
        Order order = orderRepository.saveAndFlush(
                new Order("Integration Test Laptop", 1)
        );

        Long orderId = order.getId();

        assertEquals(OrderStatus.PENDING, order.getStatus());

        orderProcessingService.processOrder(orderId);

        Order persistedOrder = orderRepository.findById(orderId)
                .orElseThrow();

        assertEquals(
                OrderStatus.COMPLETED,
                persistedOrder.getStatus()
        );

        verify(paymentService).processPayment(1);
    }

    @Test
    void exhaustedPaymentRetriesPersistFailedOrderStatus() {
        Order order = orderRepository.saveAndFlush(
                new Order("Integration Test Phone", 1)
        );

        Long orderId = order.getId();

        assertEquals(OrderStatus.PENDING, order.getStatus());

        doThrow(
                new PaymentService.TemporaryPaymentException(
                        "Payment provider failed after all retries"
                )
        ).when(paymentService).processPayment(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderProcessingService.processOrder(orderId)
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        Order persistedOrder = orderRepository.findById(orderId)
                .orElseThrow();

        assertEquals(
                OrderStatus.FAILED,
                persistedOrder.getStatus(),
                "The FAILED status should remain persisted in MySQL"
        );

        verify(paymentService).processPayment(1);
    }

    @Test
    void concurrentProcessingRequestsAttemptPaymentOnlyOnce()
            throws Exception {

        Order order = orderRepository.saveAndFlush(
                new Order("Concurrent Test Laptop", 1)
        );

        Long orderId = order.getId();

        CountDownLatch paymentStarted = new CountDownLatch(1);
        CountDownLatch allowPaymentToFinish = new CountDownLatch(1);

        doAnswer(invocation -> {
            paymentStarted.countDown();

            boolean released = allowPaymentToFinish.await(
                    10,
                    TimeUnit.SECONDS
            );

            if (!released) {
                throw new IllegalStateException(
                        "Timed out waiting for test to release payment"
                );
            }

            return "Payment successful";
        }).when(paymentService).processPayment(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Start the first request and pause it during payment.
            Future<Order> firstRequest = executor.submit(
                    () -> orderProcessingService.processOrder(orderId)
            );

            boolean paymentReached = paymentStarted.await(
                    5,
                    TimeUnit.SECONDS
            );

            assertEquals(
                    true,
                    paymentReached,
                    "The first request should reach payment processing"
            );

            // While the first payment is paused, submit a duplicate request.
            Future<HttpStatus> secondRequest = executor.submit(() -> {
                ResponseStatusException exception = assertThrows(
                        ResponseStatusException.class,
                        () -> orderProcessingService.processOrder(orderId)
                );

                return HttpStatus.valueOf(exception.getStatusCode().value());
            });

            HttpStatus secondStatus = secondRequest.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertEquals(HttpStatus.CONFLICT, secondStatus);

            // Allow the first request to finish successfully.
            allowPaymentToFinish.countDown();

            Order completedOrder = firstRequest.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertEquals(
                    OrderStatus.COMPLETED,
                    completedOrder.getStatus()
            );

            Order persistedOrder = orderRepository.findById(orderId)
                    .orElseThrow();

            assertEquals(
                    OrderStatus.COMPLETED,
                    persistedOrder.getStatus()
            );

            // Both requests shared the same mocked payment service.
            verify(paymentService, times(1)).processPayment(1);

        } finally {
            // Avoid leaving the first request blocked if an assertion fails.
            allowPaymentToFinish.countDown();
            executor.shutdownNow();
        }
    }
}
