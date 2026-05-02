package com.reservation.domain.outbox

enum class OutboxEventType {
    PAYMENT_RESULT,
    COMPENSATION_FAILURE,
}
