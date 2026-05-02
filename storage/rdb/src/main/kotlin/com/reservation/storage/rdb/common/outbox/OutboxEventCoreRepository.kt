package com.reservation.storage.rdb.common.outbox

import com.reservation.domain.outbox.OutboxEvent
import com.reservation.domain.outbox.OutboxEventRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class OutboxEventCoreRepository(
    private val jpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {
    /*
     * 보상 실패 근거는 이후 주문/재고 복구 트랜잭션이 실패해도 남아야 하므로 독립 트랜잭션으로 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun save(event: OutboxEvent): OutboxEvent = jpaRepository.save(OutboxEventEntity.from(event)).toDomain()

    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: Long): List<OutboxEvent> = jpaRepository.findAllByOrderId(orderId).map { it.toDomain() }
}
