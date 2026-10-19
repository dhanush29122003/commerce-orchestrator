
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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

        // Reload from MySQL after the service has returned its error.
        Order persistedOrder = orderRepository.findById(orderId)
                .orElseThrow();

        assertEquals(
                OrderStatus.FAILED,
                persistedOrder.getStatus(),
                "The FAILED status should remain persisted in MySQL"
        );

        verify(paymentService).processPayment(1);
    }
}
