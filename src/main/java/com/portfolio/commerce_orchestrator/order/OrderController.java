
package com.portfolio.commerce_orchestrator.order;

import com.portfolio.commerce_orchestrator.service.IdempotencyService;
import com.portfolio.commerce_orchestrator.order.OrderStatus;



import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;

    public OrderController(
            OrderRepository orderRepository,
            IdempotencyService idempotencyService) {

        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
    }

    // Create a new order.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters"
            );
        }

        return idempotencyService.createOrder(
                idempotencyKey,
                request.productName(),
                request.quantity()
        );
    }

    // Retrieve one order by ID.
    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return findOrder(id);
    }

    // Retrieve all orders.
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Move a PENDING order to PROCESSING.
    @PostMapping("/{id}/process")
    public Order processOrder(@PathVariable Long id) {
        Order order = findOrder(id);

        try {
            order.startProcessing();
            return orderRepository.save(order);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }
    }

    // Move a PROCESSING order to COMPLETED.
    @PostMapping("/{id}/complete")
    public Order completeOrder(@PathVariable Long id) {
        Order order = findOrder(id);

        try {
            order.complete();
            return orderRepository.save(order);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }
    }

    // Mark a PENDING or PROCESSING order as FAILED.
    @PostMapping("/{id}/fail")
    public Order failOrder(@PathVariable Long id) {
        Order order = findOrder(id);

        try {
            order.fail();
            return orderRepository.save(order);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }
    }

    // Shared lookup method.
    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Request body validation.
    public record CreateOrderRequest(
            @NotBlank(message = "Product name is required")
            String productName,

            @Min(value = 1, message = "Quantity must be at least 1")
            int quantity
    ) {}

    // Return HTTP 404 when an order doesn't exist.
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class OrderNotFoundException extends RuntimeException {

        public OrderNotFoundException(Long id) {
            super("Order not found with id: " + id);
        }
    }
}
