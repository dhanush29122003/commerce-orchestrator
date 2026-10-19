
package com.portfolio.commerce_orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryServiceTests {

    private final RetryService retryService = new RetryService(3, 500);

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retryService.executeWithRetry(
                () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new TemporaryFailureException(
                                "Temporary failure"
                        );
                    }
                    return "Success";
                },
                TemporaryFailureException.class
        );

        assertEquals("Success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldStopAfterThreeFailedAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                TemporaryFailureException.class,
                () -> retryService.executeWithRetry(
                        () -> {
                            attempts.incrementAndGet();
                            throw new TemporaryFailureException(
                                    "Service unavailable"
                            );
                        },
                        TemporaryFailureException.class
                )
        );

        assertEquals(3, attempts.get());
    }

    @Test
    void shouldNotRetryNonRetryableException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                IllegalArgumentException.class,
                () -> retryService.executeWithRetry(
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalArgumentException(
                                    "Invalid request"
                            );
                        },
                        TemporaryFailureException.class
                )
        );

        assertEquals(1, attempts.get());
    }

    private static class TemporaryFailureException
            extends RuntimeException {

        TemporaryFailureException(String message) {
            super(message);
        }
    }
}
