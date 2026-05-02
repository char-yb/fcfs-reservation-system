package com.reservation.application.fixture

import com.reservation.domain.outbox.OutboxEvent
import com.reservation.domain.outbox.OutboxEventRepository

class FakeOutboxEventRepository : OutboxEventRepository {
    val events = mutableListOf<OutboxEvent>()
    private var nextId = 1L

    override fun save(event: OutboxEvent): OutboxEvent {
        val saved = event.copy(id = nextId++)
        events.add(saved)
        return saved
    }

    override fun findByOrderId(orderId: Long): List<OutboxEvent> = events.filter { it.orderId == orderId }
}
