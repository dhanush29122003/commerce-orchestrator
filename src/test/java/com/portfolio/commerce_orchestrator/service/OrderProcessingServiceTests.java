
package com.portfolio.commerce_orchestrator.service;

import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.order.OrderStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderProcessingServiceTests {

    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private TransactionTemplate transactionTemplate;
    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        transactionTemplate = mock(TransactionTemplate.class);

        orderProcessingService = new OrderProcessingService(
                orderRepository,
                paymentService,
                transactionTemplate
        );

        when(transactionTemplate.execute(
                any(TransactionCallback.class)
        )).thenAnswer(invocation -> {
            TransactionCallback<?> callback =
                    invocation.getArgument(0);

            return callback.doInTransaction(
                    mock(TransactionStatus.class)
            );
        });

        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback =
                    invocation.getArgument(0);

            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(
                any(Consumer.class)
        );
    }

    @Test
    void processOrderCompletesWhenPaymentSucceeds() {
        Order order = new Order("Laptop", 1);

        when(orderRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderProcessingService.processOrder(1L);

        assertEquals(OrderStatus.COMPLETED, result.getStatus());

        verify(paymentService).processPayment(1);
        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void processOrderMarksOrderFailedWhenPaymentRetriesAreExhausted() {
        Order order = new Order("Laptop", 1);

        when(orderRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentService.processPayment(1))
                .thenThrow(
                        new PaymentService.TemporaryPaymentException(
                                "Payment failed after retries"
                        )
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderProcessingService.processOrder(1L)
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(OrderStatus.FAILED, order.getStatus());

        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void processOrderReturnsConflictForInvalidStateTransition() {
        Order order = new Order("Laptop", 1);
        order.startProcessing();
        order.complete();

        when(orderRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderProcessingService.processOrder(1L)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
    }
}
