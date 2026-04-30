package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.booking.result.BookingResult
import com.reservation.application.order.OrderService
import com.reservation.application.payment.PaymentService
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockLockService
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import org.springframework.stereotype.Component

@Component
class BookingFacade(
    private val productService: ProductService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val stockLockService: StockLockService,
) {
    private val log by logger()

    fun booking(command: BookingCommand): BookingResult {
        productService.getProduct(command.productId).validateSaleOpen()
        return stockLockService.executeWithStockLock(command.productId) {
            val stockDecremented = productService.decrementStock(command.productId)
            if (!stockDecremented) throw ErrorException(ErrorType.STOCK_SOLD_OUT)

            val order =
                try {
                    orderService.create(
                        productId = command.productId,
                        userId = command.userId,
                        totalAmount = command.totalAmount,
                        orderKey = command.orderKey,
                    )
                } catch (e: Exception) {
                    // DUPLICATE_REQUEST 등으로 실패해도 L3는 이미 차감된 상태이므로 복구한다.
                    log.warn(e) { "주문 생성 실패 orderKey=${command.orderKey}, L3 재고 복구" }
                    productService.incrementStock(command.productId)
                    throw e
                }

            try {
                val paymentResults = paymentService.execute(command.payments, command.totalAmount)
                val confirmed = orderService.confirm(order.id)
                BookingResult(orderId = confirmed.id, status = confirmed.status, payments = paymentResults)
            } catch (e: Exception) {
                log.warn(e) { "결제 실패 orderId=${order.id}, L3 재고 복구" }
                orderService.fail(order.id)
                productService.incrementStock(command.productId)
                // L1 카운터 복구는 StockLockService.executeWithStockLock 의 outer catch가 담당한다.
                throw e
            }
        }
    }
}
