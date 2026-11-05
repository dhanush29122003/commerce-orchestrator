
package com.portfolio.commerce_orchestrator.outbox;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.status = :status
            ORDER BY event.createdAt ASC
            """)
    List<OutboxEvent> findPendingEventsForUpdate(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE status = 'PENDING'
              AND (
                    claimed_until IS NULL
                    OR claimed_until <= :currentTime
                  )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findClaimableEventsForUpdate(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
            UPDATE OutboxEvent event
            SET event.status = :publishedStatus,
                event.publishedAt = :publishedAt,
                event.lastError = null,
                event.claimedBy = null,
                event.claimedUntil = null
            WHERE event.id = :eventId
              AND event.status = :pendingStatus
              AND event.claimedBy = :publisherId
              AND event.claimedUntil > :currentTime
            """)
    int markPublishedIfOwned(
            @Param("eventId") Long eventId,
            @Param("publisherId") String publisherId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Modifying
    @Query("""
            UPDATE OutboxEvent event
            SET event.attempts = event.attempts + 1,
                event.lastError = :errorMessage,
                event.claimedBy = null,
                event.claimedUntil = null
            WHERE event.id = :eventId
              AND event.status = :pendingStatus
              AND event.claimedBy = :publisherId
              AND event.claimedUntil > :currentTime
            """)
    int recordFailureIfOwned(
            @Param("eventId") Long eventId,
            @Param("publisherId") String publisherId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("errorMessage") String errorMessage,
            @Param("currentTime") LocalDateTime currentTime
    );
}
