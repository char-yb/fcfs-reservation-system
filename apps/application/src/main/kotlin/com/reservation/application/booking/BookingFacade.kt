package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.booking.result.BookingResult
import com.reservation.application.payment.PaymentService
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.domain.payment.PaymentExecutionResult
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
            var paymentResults = emptyList<PaymentExecutionResult>()
            try {
                paymentResults = paymentService.execute(command.payments, command.totalAmount, order.id)
                paymentService.saveApproved(order.id, paymentResults)
                val confirmed = bookingReservationProcessor.confirm(order.id)
                BookingResult(orderId = confirmed.id, status = confirmed.status, payments = paymentResults)
            } catch (e: Exception) {
                log.warn(e) { "예약 결제/확정 실패 orderId=${order.id}, DB 재고 복구" }
                if (paymentResults.isNotEmpty()) {
                    val compensationFailures = paymentService.compensate(order.id, paymentResults)
                    paymentService.markCancelled(order.id, paymentResults - compensationFailures.toSet())
                }
                runCatching {
                    bookingReservationProcessor.failAndRelease(order.id, command.productId)
                }.onFailure { recoveryFailure ->
                    log.error(recoveryFailure) { "예약 실패 후 DB 복구 실패 orderId=${order.id}" }
                }
                // L1 카운터 복구는 StockService.executeWithStockGuard 의 outer catch가 담당한다.
                throw e
            }
        }
    }
}
