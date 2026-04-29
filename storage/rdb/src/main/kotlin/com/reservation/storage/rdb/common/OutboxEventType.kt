package com.reservation.storage.rdb.common

enum class OutboxEventType {
    PAYMENT_RESULT,
    COMPENSATION_FAILURE,
}
