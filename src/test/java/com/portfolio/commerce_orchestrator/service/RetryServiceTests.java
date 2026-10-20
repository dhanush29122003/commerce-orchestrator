
package com.portfolio.commerce_orchestrator.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryServiceTests {

    private SimpleMeterRegistry meterRegistry;
    private RetryService retryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        retryService = new RetryService(3, 0, meterRegistry);
    }

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
        assertEquals(
                1.0,
                meterRegistry.counter("commerce.retry.attempts").count()
        );
        assertEquals(
                1.0,
                meterRegistry.counter(
                        "commerce.retry.success.after.retry"
                ).count()
        );
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
        assertEquals(
                2.0,
                meterRegistry.counter("commerce.retry.attempts").count()
        );
        assertEquals(
                1.0,
                meterRegistry.counter("commerce.retry.exhausted").count()
        );
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
        assertEquals(
                0.0,
                meterRegistry.counter("commerce.retry.attempts").count()
        );
    }

    private static class TemporaryFailureException
            extends RuntimeException {

        TemporaryFailureException(String message) {
            super(message);
        }
    }
}
