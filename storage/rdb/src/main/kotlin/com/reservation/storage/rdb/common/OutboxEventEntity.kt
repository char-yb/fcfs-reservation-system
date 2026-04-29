package com.reservation.storage.rdb.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_type", columnList = "status, event_type"),
        Index(name = "idx_outbox_order_id", columnList = "order_id"),
    ],
)
class OutboxEventEntity(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    val eventType: OutboxEventType,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
