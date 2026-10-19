
package com.portfolio.commerce_orchestrator.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentService {

    private final RetryService retryService;

    public PaymentService(RetryService retryService) {
        this.retryService = retryService;
    }

    public String processPayment(int failuresBeforeSuccess) {
        if (failuresBeforeSuccess < 0) {
            throw new IllegalArgumentException(
                    "Failure count cannot be negative"
            );
        }

        AtomicInteger attempts = new AtomicInteger();

        return retryService.executeWithRetry(
                () -> {
                    int currentAttempt = attempts.incrementAndGet();

                    if (currentAttempt <= failuresBeforeSuccess) {
                        throw new TemporaryPaymentException(
                                "Temporary payment provider failure"
                        );
                    }

                    return "Payment successful";
                },
                TemporaryPaymentException.class
        );
    }

    public static class TemporaryPaymentException
            extends RuntimeException {

        public TemporaryPaymentException(String message) {
            super(message);
        }
    }
}
