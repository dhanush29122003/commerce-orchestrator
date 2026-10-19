
package com.portfolio.commerce_orchestrator.order;

import com.portfolio.commerce_orchestrator.service.IdempotencyService;
import com.portfolio.commerce_orchestrator.service.OrderProcessingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final OrderProcessingService orderProcessingService;

    public OrderController(
            OrderRepository orderRepository,
            IdempotencyService idempotencyService,
            OrderProcessingService orderProcessingService) {
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
        this.orderProcessingService = orderProcessingService;
    }

    public record CreateOrderRequest(
            @NotBlank(message = "Product name must not be blank")
            String productName,

            @Min(value = 1, message = "Quantity must be at least 1")
            int quantity) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters");
        }

        return idempotencyService.createOrder(
                idempotencyKey,
                request.productName(),
                request.quantity());
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found with id: " + id));
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @PostMapping("/{id}/process")
    public Order processOrder(@PathVariable Long id) {
        return orderProcessingService.processOrder(id);
    }

    @PostMapping("/{id}/complete")
    public Order completeOrder(@PathVariable Long id) {
        return orderProcessingService.completeOrder(id);
    }

    @PostMapping("/{id}/fail")
    public Order failOrder(@PathVariable Long id) {
        return orderProcessingService.failOrder(id);
    }
}
