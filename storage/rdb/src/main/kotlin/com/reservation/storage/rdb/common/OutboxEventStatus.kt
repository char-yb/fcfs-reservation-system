package com.reservation.storage.rdb.common

enum class OutboxEventStatus {
    PENDING,
    PROCESSED,
    FAILED,
}
