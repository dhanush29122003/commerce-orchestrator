
package com.portfolio.commerce_orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentServiceTests {

    private final PaymentService paymentService =
            new PaymentService(new RetryService(3, 500));

    @Test
    void shouldSucceedAfterOneTemporaryPaymentFailure() {
        String result = paymentService.processPayment(1);

        assertEquals("Payment successful", result);
    }

    @Test
    void shouldSucceedAfterTwoTemporaryPaymentFailures() {
        String result = paymentService.processPayment(2);

        assertEquals("Payment successful", result);
    }

    @Test
    void shouldFailAfterThreeTemporaryPaymentFailures() {
        assertThrows(
                PaymentService.TemporaryPaymentException.class,
                () -> paymentService.processPayment(3)
        );
    }

    @Test
    void shouldRejectNegativeFailureCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.processPayment(-1)
        );
    }
}
