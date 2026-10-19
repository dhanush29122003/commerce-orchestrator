
package com.portfolio.commerce_orchestrator;

import com.portfolio.commerce_orchestrator.order.OrderController;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import com.portfolio.commerce_orchestrator.service.IdempotencyService;
import com.portfolio.commerce_orchestrator.service.OrderProcessingService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @MockitoBean
    private OrderProcessingService orderProcessingService;

    @Test
    void missingOrderShouldReturnNotFound() throws Exception {
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void completingMissingOrderShouldReturnNotFound() throws Exception {
        when(orderProcessingService.completeOrder(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));

        mockMvc.perform(post("/api/orders/999/complete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void completingAlreadyCompletedOrderShouldReturnConflict()
            throws Exception {
        when(orderProcessingService.completeOrder(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot complete an already completed order"));

        mockMvc.perform(post("/api/orders/1/complete"))
                .andExpect(status().isConflict());
    }

    @Test
    void processingCompletedOrderShouldReturnConflict()
            throws Exception {
        when(orderProcessingService.processOrder(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot process a completed order"));

        mockMvc.perform(post("/api/orders/1/process"))
                .andExpect(status().isConflict());
    }

    @Test
    void blankProductNameShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "test-key-1")
                        .contentType("application/json")
                        .content("""
                                {
                                    "productName": " ",
                                    "quantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroQuantityShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "test-key-2")
                        .contentType("application/json")
                        .content("""
                                {
                                    "productName": "Laptop",
                                    "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdempotencyKeyShouldReturnBadRequest()
            throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("""
                                {
                                    "productName": "Laptop",
                                    "quantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
