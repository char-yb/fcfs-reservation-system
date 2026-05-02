package com.reservation.application.order

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.money.Money
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun create(
        userId: Long,
        totalAmount: Money,
        orderKey: String,
    ): Order {
        val existing = orderRepository.findByOrderKey(orderKey)
        if (existing != null) throw ErrorException(ErrorType.DUPLICATE_REQUEST)
        return orderRepository.save(
            Order(
                id = 0L,
                userId = userId,
                totalAmount = totalAmount,
                status = OrderStatus.PENDING,
                orderKey = orderKey,
            ),
        )
    }

    @Transactional
    fun confirm(orderId: Long): Order = transitionPendingOrder(orderId, OrderStatus.CONFIRMED)

    @Transactional
    fun fail(orderId: Long): Order = transitionPendingOrder(orderId, OrderStatus.FAILED)

    private fun transitionPendingOrder(
        orderId: Long,
        nextStatus: OrderStatus,
    ): Order =
        orderRepository.updateStatusIfCurrent(
            id = orderId,
            currentStatus = OrderStatus.PENDING,
            nextStatus = nextStatus,
        ) ?: throw ErrorException(ErrorType.INVALID_ORDER_STATUS_TRANSITION)
}
