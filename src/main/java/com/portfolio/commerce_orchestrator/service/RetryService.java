
package com.portfolio.commerce_orchestrator.service;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class RetryService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 500;

    public <T> T executeWithRetry(
            Supplier<T> operation,
            Class<? extends RuntimeException> retryableException
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                boolean isRetryable =
                        retryableException.isInstance(exception);

                if (!isRetryable || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }

                long delay = INITIAL_DELAY_MS * (1L << (attempt - 1));

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
