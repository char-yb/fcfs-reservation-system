package com.reservation.application.booking

import com.reservation.application.order.OrderService
import com.reservation.application.payment.PaymentOrchestrator
import com.reservation.application.product.ProductService
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import org.springframework.stereotype.Component

@Component
class BookingFacade(
    private val productService: ProductService,
    private val orderService: OrderService,
    private val paymentOrchestrator: PaymentOrchestrator,
) {
    private val log by logger()

    fun book(command: BookingCommand): BookingResult {
        val product = productService.getProduct(command.productId)
        product.validateSaleOpen()

        // TODO: L1 Redis atomic counter 선점 (02-concurrency-and-locking.md)
        // TODO: L2 Redisson distributed lock (02-concurrency-and-locking.md)

        // L3: 재고 조건부 차감 (remaining_quantity > 0 보장)
        val stockDecremented = productService.decrementStock(command.productId)
        if (!stockDecremented) throw ErrorException(ErrorType.STOCK_SOLD_OUT)

        val order =
            orderService.createPending(
                productId = command.productId,
                userId = command.userId,
                totalAmount = command.totalAmount,
                orderKey = command.orderKey,
            )

        return try {
            val paymentResults = paymentOrchestrator.execute(command.payments, command.totalAmount)
            val confirmed = orderService.confirm(order.id)
            BookingResult(orderId = confirmed.id, status = confirmed.status, payments = paymentResults)
        } catch (e: ErrorException) {
            log.warn { "결제 실패 orderId=${order.id}, 재고 복구" }
            orderService.fail(order.id)
            productService.incrementStock(command.productId)
            throw e
        }
    }
}
