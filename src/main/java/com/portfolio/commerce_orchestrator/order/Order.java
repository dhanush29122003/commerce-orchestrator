
package com.portfolio.commerce_orchestrator.order;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Order() {
    }

    public Order(String productName, int quantity) {
        this.productName = productName;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void startProcessing() {
        transitionTo(OrderStatus.PROCESSING);
    }

    public void complete() {
        transitionTo(OrderStatus.COMPLETED);
    }

    public void fail() {
        transitionTo(OrderStatus.FAILED);
    }

    private void transitionTo(OrderStatus newStatus) {

        boolean validTransition = switch (status) {
            case PENDING -> newStatus == OrderStatus.PROCESSING
                    || newStatus == OrderStatus.FAILED;

            case PROCESSING -> newStatus == OrderStatus.COMPLETED
                    || newStatus == OrderStatus.FAILED;

            case COMPLETED, FAILED -> false;
        };

        if (!validTransition) {
            throw new IllegalStateException(
                    "Cannot transition order from "
                            + status + " to " + newStatus
            );
        }

        this.status = newStatus;
    }
}
