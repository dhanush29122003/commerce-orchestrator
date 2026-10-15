
package com.portfolio.commerce_orchestrator;

import com.portfolio.commerce_orchestrator.service.IdempotencyService;
import com.portfolio.commerce_orchestrator.order.Order;
import com.portfolio.commerce_orchestrator.order.OrderController;
import com.portfolio.commerce_orchestrator.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void missingOrderShouldReturnNotFound() throws Exception {
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void completingMissingOrderShouldReturnNotFound() throws Exception {
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/orders/999/complete"))
                .andExpect(status().isNotFound());
    }
    
@Test
void completingAlreadyCompletedOrderShouldReturnConflict() throws Exception {
    Order order = new Order("Laptop", 1);
    order.startProcessing();
    order.complete();

    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));

    mockMvc.perform(post("/api/orders/1/complete"))
            .andExpect(status().isConflict());

    verify(orderRepository, never()).save(order);
}

@Test
void processingCompletedOrderShouldReturnConflict() throws Exception {
    Order order = new Order("Phone", 1);
    order.startProcessing();
    order.complete();

    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));

    mockMvc.perform(post("/api/orders/1/process"))
            .andExpect(status().isConflict());

    verify(orderRepository, never()).save(order);
}

@Test
void blankProductNameShouldReturnBadRequest() throws Exception {
    mockMvc.perform(post("/api/orders")
                    .header("Idempotency-Key", "test-key-101")
                    .contentType("application/json")
                    .content("""
                            {
                                "productName": "   ",
                                "quantity": 1
                            }
                            """))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(idempotencyService);
}

@Test
void zeroQuantityShouldReturnBadRequest() throws Exception {
    mockMvc.perform(post("/api/orders")
                    .header("Idempotency-Key", "test-key-102")
                    .contentType("application/json")
                    .content("""
                            {
                                "productName": "Laptop",
                                "quantity": 0
                            }
                            """))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(idempotencyService);
}

@Test
void missingIdempotencyKeyShouldReturnBadRequest() throws Exception {
    mockMvc.perform(post("/api/orders")
                    .contentType("application/json")
                    .content("""
                            {
                                "productName": "Laptop",
                                "quantity": 1
                            }
                            """))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(idempotencyService);
}

}
