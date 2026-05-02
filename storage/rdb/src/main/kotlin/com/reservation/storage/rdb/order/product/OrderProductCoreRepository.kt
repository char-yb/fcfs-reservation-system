package com.reservation.storage.rdb.order.product

import com.reservation.domain.order.OrderProduct
import com.reservation.domain.order.OrderProductRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderProductCoreRepository(
    private val jpaRepository: OrderProductJpaRepository,
) : OrderProductRepository {
    override fun save(orderProduct: OrderProduct): OrderProduct = jpaRepository.save(OrderProductEntity.from(orderProduct)).toDomain()

    override fun findByOrderId(orderId: Long): List<OrderProduct> = jpaRepository.findByOrderId(orderId).map { it.toDomain() }

    override fun markConfirmedByOrderId(
        orderId: Long,
        confirmedAt: LocalDateTime,
    ) {
        jpaRepository.markConfirmedByOrderId(orderId, confirmedAt)
    }

    override fun markCanceledByOrderId(
        orderId: Long,
        canceledAt: LocalDateTime,
    ) {
        jpaRepository.markCanceledByOrderId(orderId, canceledAt)
    }
}
