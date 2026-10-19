
package com.portfolio.commerce_orchestrator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class RetryService {

    private final int maxAttempts;
    private final long initialDelayMs;

    public RetryService(
            @Value("${app.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.retry.initial-delay-ms:500}") long initialDelayMs
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be at least 1"
            );
        }

        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Initial delay cannot be negative"
            );
        }

        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    public <T> T executeWithRetry(
            Supplier<T> operation,
            Class<? extends RuntimeException> retryableException
    ) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                boolean isRetryable =
                        retryableException.isInstance(exception);

                if (!isRetryable || attempt == maxAttempts) {
                    throw exception;
                }

                long delay = initialDelayMs * (1L << (attempt - 1));

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Retry interrupted",
                            interruptedException
                    );
                }
            }
        }

        throw new IllegalStateException("Retry attempts exhausted");
    }
}
