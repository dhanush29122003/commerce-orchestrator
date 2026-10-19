
package com.portfolio.commerce_orchestrator.service;

import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;

    public OrderProcessingService(
            OrderRepository orderRepository,
            PaymentService paymentService,
            TransactionTemplate transactionTemplate
    ) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.transactionTemplate = transactionTemplate;
    }

    public Order processOrder(Long id) {
        final Order processingOrder;

        try {
            processingOrder = transactionTemplate.execute(status -> {
                Order order = findOrderForUpdate(id);
                order.startProcessing();
                return orderRepository.save(order);
            });
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }

        if (processingOrder == null) {
            throw new IllegalStateException(
                    "Could not start order processing"
            );
        }

        try {
            // Simulated payment: one temporary failure, then success.
            paymentService.processPayment(1);

            Order completedOrder = transactionTemplate.execute(status -> {
                Order order = findOrderForUpdate(id);
                order.complete();
                return orderRepository.save(order);
            });

            if (completedOrder == null) {
                throw new IllegalStateException(
                        "Could not complete order processing"
                );
            }

            return completedOrder;

        } catch (PaymentService.TemporaryPaymentException exception) {
            // Persist FAILED in a separate transaction before returning 503.
            transactionTemplate.executeWithoutResult(status -> {
                Order order = findOrderForUpdate(id);
                order.fail();
                orderRepository.save(order);
            });

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment failed after retries",
                    exception
            );

        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    @Transactional
    public Order completeOrder(Long id) {
        Order order = findOrder(id);

        try {
            order.complete();
            return orderRepository.save(order);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    @Transactional
    public Order failOrder(Long id) {
        Order order = findOrder(id);

        try {
            order.fail();
            return orderRepository.save(order);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found with id: " + id
                ));
    }

    private Order findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found with id: " + id
                ));
    }
}
