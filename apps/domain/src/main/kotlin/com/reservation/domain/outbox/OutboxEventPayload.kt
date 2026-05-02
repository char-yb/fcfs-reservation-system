package com.reservation.domain.outbox

sealed interface OutboxEventPayload

data class RawOutboxEventPayload(
    val value: String,
) : OutboxEventPayload {
    override fun toString(): String = value
}
