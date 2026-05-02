package com.reservation.domain.outbox

enum class OutboxEventStatus {
    PENDING,
    PROCESSED,
    FAILED,
}
