package com.reservation.domain.outbox

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent

    fun findByOrderId(orderId: Long): List<OutboxEvent>
}
