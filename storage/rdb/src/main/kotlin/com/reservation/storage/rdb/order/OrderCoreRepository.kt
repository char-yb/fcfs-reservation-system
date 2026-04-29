package com.reservation.storage.rdb.order

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Repository

@Repository
class OrderCoreRepository(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun findById(id: Long): Order? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByOrderKey(orderKey: String): Order? = jpaRepository.findByOrderKey(orderKey).orElse(null)?.toDomain()

    override fun save(order: Order): Order {
        val entity =
            OrderEntity.create(
                productId = order.productId,
                userId = order.userId,
                totalAmount = order.totalAmount,
                orderKey = order.orderKey,
            )
        return jpaRepository.save(entity).toDomain()
    }

    override fun updateStatus(
        id: Long,
        status: OrderStatus,
    ): Order {
        val entity =
            jpaRepository.findById(id).orElseThrow {
                ErrorException(ErrorType.INVALID_REQUEST)
            }
        entity.status = status
        return jpaRepository.save(entity).toDomain()
    }
}
