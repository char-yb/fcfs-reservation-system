package com.reservation.storage.rdb.common.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findAllByOrderId(orderId: Long): List<OutboxEventEntity>
}
