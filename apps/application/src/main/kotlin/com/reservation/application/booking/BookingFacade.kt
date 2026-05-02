package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.booking.result.BookingResult
import com.reservation.application.payment.PaymentService
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.domain.order.Order
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
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
        val bookingOption = productService.getBookingOption(command.productOptionId)
        bookingOption.validateSaleOpen()
        if (!command.totalAmount.isEqualsThan(bookingOption.price)) {
            throw ErrorException(ErrorType.ORDER_AMOUNT_INVALID)
        }

        lateinit var reservedOrder: Order
        val redisCounterReserved =
            stockService.executeWithStockReservation(
                productOptionId = command.productOptionId,
                action = {
                    reservedOrder = bookingReservationProcessor.reserve(command, bookingOption)
                },
                fallbackAction = {
                    reservedOrder = bookingReservationProcessor.reserve(command, bookingOption)
                },
            )

        var paymentResults = emptyList<PaymentExecutionResult>()
        try {
            paymentResults = paymentService.execute(command.payments, command.totalAmount, reservedOrder.id)
            paymentService.saveApproved(reservedOrder.id, paymentResults)

            val confirmed = bookingReservationProcessor.confirm(reservedOrder.id)
            return BookingResult(orderId = confirmed.id, status = confirmed.status, payments = paymentResults)
        } catch (e: Exception) {
            log.warn(e) { "예약 결제/확정 실패 orderId=${reservedOrder.id}, DB 재고 복구" }
            if (paymentResults.isNotEmpty()) {
                val compensationFailures = paymentService.compensate(reservedOrder.id, paymentResults)
                paymentService.markCancelled(reservedOrder.id, paymentResults - compensationFailures.toSet())
            }
            runCatching {
                bookingReservationProcessor.failAndRelease(reservedOrder.id, command.productOptionId)
            }.onSuccess {
                if (redisCounterReserved) {
                    stockService.releaseStockReservation(command.productOptionId)
                }
            }.onFailure { recoveryFailure ->
                log.error(recoveryFailure) { "예약 실패 후 DB 복구 실패 orderId=${reservedOrder.id}" }
            }
            throw e
        }
    }
}
