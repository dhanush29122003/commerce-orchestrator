
package com.portfolio.commerce_orchestrator.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_until")
    private LocalDateTime claimedUntil;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String eventType,
            String aggregateType,
            String aggregateId,
            String payload) {

        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void claim(String publisherId, LocalDateTime claimExpiry) {
        if (status != OutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending events can be claimed."
            );
        }

        this.claimedBy = publisherId;
        this.claimedUntil = claimExpiry;
    }

    public void releaseClaim() {
        this.claimedBy = null;
        this.claimedUntil = null;
    }

    public boolean isClaimExpired(LocalDateTime currentTime) {
        return claimedUntil != null
                && !claimedUntil.isAfter(currentTime);
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
        releaseClaim();
    }

    public void recordFailure(String errorMessage) {
        this.attempts++;
        this.lastError = errorMessage;
        releaseClaim();
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getPayload() {
        return payload;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public LocalDateTime getClaimedUntil() {
        return claimedUntil;
    }
}
