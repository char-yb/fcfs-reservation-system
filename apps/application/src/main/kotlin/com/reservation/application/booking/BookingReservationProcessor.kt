package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.product.ProductStockRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class BookingReservationProcessor(
    private val productStockRepository: ProductStockRepository,
    private val orderRepository: OrderRepository,
) {
    /*
     * 분산락 내부의 DB 변경은 상위 트랜잭션에 합류하지 않는다.
     * MySQL REPEATABLE READ 환경에서 락 해제 후 커밋이 밀리면 다음 요청이 오래된 snapshot으로
     * 진입할 수 있으므로, 락 안의 변경은 독립 트랜잭션으로 시작하고 락 해제 전에 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserve(command: BookingCommand): Order {
        val existing = orderRepository.findByOrderKey(command.orderKey)
        if (existing != null) throw ErrorException(ErrorType.DUPLICATE_REQUEST)

        val stockDecremented = productStockRepository.decrementStock(command.productId)
        if (!stockDecremented) throw ErrorException(ErrorType.STOCK_SOLD_OUT)

        return orderRepository.save(
            Order(
                id = 0L,
                productId = command.productId,
                userId = command.userId,
                totalAmount = command.totalAmount,
                status = OrderStatus.PENDING,
                orderKey = command.orderKey,
            ),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun confirm(orderId: Long): Order = orderRepository.updateStatus(orderId, OrderStatus.CONFIRMED)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun failAndRelease(
        orderId: Long,
        productId: Long,
    ): Order {
        val failed = orderRepository.updateStatus(orderId, OrderStatus.FAILED)
        productStockRepository.incrementStock(productId)
        return failed
    }
}
