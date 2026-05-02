package com.reservation.domain.outbox

data class OutboxEvent(
    val id: Long = 0,
    val orderId: Long,
    val eventType: OutboxEventType,
    val payload: OutboxEventPayload,
    val status: OutboxEventStatus = OutboxEventStatus.PENDING,
    val retryCount: Int = 0,
)
