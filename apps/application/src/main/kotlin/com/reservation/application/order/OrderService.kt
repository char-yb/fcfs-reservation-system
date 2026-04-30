package com.reservation.application.order

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun create(
        productId: Long,
        userId: Long,
        totalAmount: Long,
        orderKey: String,
    ): Order {
        val existing = orderRepository.findByOrderKey(orderKey)
        if (existing != null) throw ErrorException(ErrorType.DUPLICATE_REQUEST)
        return orderRepository.save(
            Order(
                id = 0L,
                productId = productId,
                userId = userId,
                totalAmount = totalAmount,
                status = OrderStatus.PENDING,
                orderKey = orderKey,
            ),
        )
    }

    @Transactional
    fun confirm(orderId: Long): Order = orderRepository.updateStatus(orderId, OrderStatus.CONFIRMED)

    @Transactional
    fun fail(orderId: Long): Order = orderRepository.updateStatus(orderId, OrderStatus.FAILED)
}
