
package com.portfolio.commerce_orchestrator.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OutboxConcurrencyIntegrationTests {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPreventTwoWorkersFromClaimingSameEvent()
            throws Exception {

        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(
                new OutboxEvent(
                        "ORDER_CREATED",
                        "ORDER",
                        "concurrency-test-002",
                        "{\"orderId\":1002}"
                )
        );

        CountDownLatch firstWorkerLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        CountDownLatch secondWorkerStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        LocalDateTime claimTime = LocalDateTime.now();

        try {
            Future<List<OutboxEvent>> firstWorker =
                    executor.submit(() ->
                            transactionTemplate.execute(status -> {

                                List<OutboxEvent> events =
                                        outboxEventRepository
                                                .findClaimableEventsForUpdate(
                                                        claimTime,
                                                        100
                                                );

                                OutboxEvent event = events.stream()
                                        .filter(item -> item.getId()
                                                .equals(savedEvent.getId()))
                                        .findFirst()
                                        .orElseThrow();

                                event.claim(
                                        "worker-1",
                                        claimTime.plusSeconds(60)
                                );

                                firstWorkerLocked.countDown();

                                try {
                                    if (!releaseFirstWorker.await(
                                            10,
                                            TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to release first worker"
                                        );
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(exception);
                                }

                                return events;
                            })
                    );

            assertThat(firstWorkerLocked.await(5, TimeUnit.SECONDS))
                    .isTrue();

            Future<List<OutboxEvent>> secondWorker =
                    executor.submit(() -> {
                        secondWorkerStarted.countDown();

                        return transactionTemplate.execute(status ->
                                outboxEventRepository
                                        .findClaimableEventsForUpdate(
                                                LocalDateTime.now(),
                                                100
                                        )
                        );
                    });

            assertThat(secondWorkerStarted.await(5, TimeUnit.SECONDS))
                    .isTrue();

            List<OutboxEvent> secondWorkerEvents =
                    secondWorker.get(5, TimeUnit.SECONDS);

            assertThat(secondWorkerEvents)
                    .extracting(OutboxEvent::getId)
                    .doesNotContain(savedEvent.getId());

            releaseFirstWorker.countDown();

            firstWorker.get(10, TimeUnit.SECONDS);

            OutboxEvent updatedEvent =
                    outboxEventRepository.findById(savedEvent.getId())
                            .orElseThrow();

            assertThat(updatedEvent.getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

            assertThat(updatedEvent.getClaimedBy())
                    .isEqualTo("worker-1");

            assertThat(updatedEvent.getClaimedUntil())
                    .isAfter(LocalDateTime.now());

        } finally {
            releaseFirstWorker.countDown();

            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            outboxEventRepository.deleteById(savedEvent.getId());
        }
    }
}
