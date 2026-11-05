
package com.portfolio.commerce_orchestrator.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class OutboxEventTests {

    @Test
    void shouldIdentifyExpiredClaim() {

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "lease-test-001",
                "{\"orderId\":2001}"
        );

        LocalDateTime now = LocalDateTime.now();

        event.claim("worker-1", now.minusSeconds(10));

        assertThat(event.isClaimExpired(now)).isTrue();
    }

    @Test
    void shouldIdentifyActiveClaim() {

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "lease-test-002",
                "{\"orderId\":2002}"
        );

        LocalDateTime now = LocalDateTime.now();

        event.claim("worker-1", now.plusSeconds(60));

        assertThat(event.isClaimExpired(now)).isFalse();
    }

    @Test
    void shouldTreatUnclaimedEventAsNotExpired() {

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "lease-test-003",
                "{\"orderId\":2003}"
        );

        assertThat(event.isClaimExpired(LocalDateTime.now()))
                .isFalse();
    }

    @Test
    void shouldReleaseClaimAfterFailure() {

        OutboxEvent event = new OutboxEvent(
                "ORDER_CREATED",
                "ORDER",
                "lease-test-004",
                "{\"orderId\":2004}"
        );

        event.claim(
                "worker-1",
                LocalDateTime.now().plusSeconds(60)
        );

        event.recordFailure("Kafka unavailable");

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Kafka unavailable");
        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimedUntil()).isNull();
    }
}
