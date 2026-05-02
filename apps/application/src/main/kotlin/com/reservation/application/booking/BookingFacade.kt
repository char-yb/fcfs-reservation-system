package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.booking.result.BookingResult
import com.reservation.application.payment.PaymentService
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.support.extension.logger
import org.springframework.stereotype.Component

@Component
class BookingFacade(
    private val productService: ProductService,
    private val bookingReservationProcessor: BookingReservationProcessor,
    private val paymentService: PaymentService,
    private val stockService: StockService,
) {
    private val log by logger()

    fun booking(command: BookingCommand): BookingResult {
        productService.getProduct(command.productId).validateSaleOpen()
        return stockService.executeWithStockGuard(command.productId) {
            val order = bookingReservationProcessor.reserve(command)
            try {
                val paymentResults = paymentService.execute(command.payments, command.totalAmount)
                val confirmed = bookingReservationProcessor.confirm(order.id)
                BookingResult(orderId = confirmed.id, status = confirmed.status, payments = paymentResults)
            } catch (e: Exception) {
                log.warn(e) { "결제 실패 orderId=${order.id}, DB 재고 복구" }
                runCatching {
                    bookingReservationProcessor.failAndRelease(order.id, command.productId)
                }.onFailure { recoveryFailure ->
                    log.error(recoveryFailure) { "결제 실패 후 DB 복구 실패 orderId=${order.id}" }
                }
                // L1 카운터 복구는 StockService.executeWithStockGuard 의 outer catch가 담당한다.
                throw e
            }
        }
    }
}
