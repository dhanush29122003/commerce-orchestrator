
package com.portfolio.commerce_orchestrator.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStatusTests {

    @Test
    void newOrderShouldStartAsPending() {
        Order order = new Order("Gaming Mouse", 1);

        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void pendingOrderCanStartProcessing() {
        Order order = new Order("Gaming Mouse", 1);

        order.startProcessing();

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
    }

    @Test
    void processingOrderCanBeCompleted() {
        Order order = new Order("Gaming Mouse", 1);

        order.startProcessing();
        order.complete();

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void pendingOrderCanFail() {
        Order order = new Order("Gaming Mouse", 1);

        order.fail();

        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    void completedOrderCannotBeProcessedAgain() {
        Order order = new Order("Gaming Mouse", 1);

        order.startProcessing();
        order.complete();

        assertThrows(
                IllegalStateException.class,
                order::startProcessing
        );

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void failedOrderCannotBeCompleted() {
        Order order = new Order("Gaming Mouse", 1);

        order.fail();

        assertThrows(
                IllegalStateException.class,
                order::complete
        );

        assertEquals(OrderStatus.FAILED, order.getStatus());
    }
}
