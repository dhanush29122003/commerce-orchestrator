
package com.portfolio.commerce_orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class RetryService {

    private static final Logger logger =
            LoggerFactory.getLogger(RetryService.class);

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
                T result = operation.get();

                if (attempt > 1) {
                    logger.info(
                            "Operation succeeded on attempt {}/{}",
                            attempt,
                            maxAttempts
                    );
                }

                return result;

            } catch (RuntimeException exception) {
                boolean isRetryable =
                        retryableException.isInstance(exception);

                if (!isRetryable) {
                    logger.warn(
                            "Operation failed with a non-retryable exception on attempt {}/{}: {}",
                            attempt,
                            maxAttempts,
                            exception.toString()
                    );

                    throw exception;
                }

                if (attempt == maxAttempts) {
                    logger.error(
                            "Operation failed after exhausting {} attempts",
                            maxAttempts,
                            exception
                    );

                    throw exception;
                }

                long delay = initialDelayMs * (1L << (attempt - 1));

                logger.warn(
                        "Retryable failure on attempt {}/{}. Retrying in {} ms. Cause: {}",
                        attempt,
                        maxAttempts,
                        delay,
                        exception.toString()
                );

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();

                    logger.warn(
                            "Retry interrupted during the wait before attempt {}",
                            attempt + 1,
                            interruptedException
                    );

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
