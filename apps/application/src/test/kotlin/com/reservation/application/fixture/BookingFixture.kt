package com.reservation.application.fixture

import com.reservation.application.booking.BookingFacade
import com.reservation.application.booking.BookingReservationProcessor
import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.payment.PaymentService
import com.reservation.application.payment.PaymentStrategyRegistry
import com.reservation.application.payment.PaymentValidator
import com.reservation.application.payment.command.PaymentCommand
import com.reservation.application.payment.fixture.RecordingPaymentStrategy
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

data class BookingFacadeFixture(
    val facade: BookingFacade,
    val orderRepository: FakeOrderRepository,
    val orderProductRepository: FakeOrderProductRepository,
    val stockRepository: FakeProductStockRepository,
    val counterRepository: FakeStockCounterRepository,
    val paymentRepository: FakePaymentRepository,
    val outboxRepository: FakeOutboxEventRepository,
    val paymentEvents: MutableList<String>,
)

fun bookingFacadeFixture(
    stockRemaining: Int = 1,
    counterRemaining: Long = 2L,
    counterDecrementFailure: RuntimeException? = null,
    orderStatusFailures: Map<OrderStatus, RuntimeException> = emptyMap(),
    paymentSaveFailure: RuntimeException? = null,
    failingMethod: PaymentMethod? = null,
    cancelFailingMethod: PaymentMethod? = null,
    events: MutableList<String> = mutableListOf(),
    recordLockEvents: Boolean = false,
): BookingFacadeFixture =
    bookingFacadeFixture(
        orderRepository =
            FakeOrderRepository(
                events = events,
                statusUpdateFailures = orderStatusFailures,
            ),
        stockRepository =
            FakeProductStockRepository(
                listOf(productStock(remainingQuantity = stockRemaining)),
                events = events,
            ),
        counterRepository =
            FakeStockCounterRepository(
                initialRemaining = counterRemaining,
                decrementFailure = counterDecrementFailure,
            ),
        paymentRepository = FakePaymentRepository(saveFailure = paymentSaveFailure, events = events),
        failingMethod = failingMethod,
        cancelFailingMethod = cancelFailingMethod,
        paymentEvents = events,
        recordLockEvents = recordLockEvents,
    )

fun bookingFacadeFixture(
    orderRepository: FakeOrderRepository,
    orderProductRepository: FakeOrderProductRepository = FakeOrderProductRepository(),
    stockRepository: FakeProductStockRepository,
    counterRepository: FakeStockCounterRepository = FakeStockCounterRepository(initialRemaining = 2L),
    paymentRepository: FakePaymentRepository = FakePaymentRepository(),
    outboxRepository: FakeOutboxEventRepository = FakeOutboxEventRepository(),
    failingMethod: PaymentMethod? = null,
    cancelFailingMethod: PaymentMethod? = null,
    paymentEvents: MutableList<String> = mutableListOf(),
    recordLockEvents: Boolean = false,
): BookingFacadeFixture {
    val productService =
        ProductService(
            productRepository =
                FakeProductRepository(
                    initialProducts = listOf(openProduct(id = 1L)),
                    initialBookingOptions = listOf(openBookingOption(id = 1L, productId = 1L)),
                ),
        )
    val strategies =
        PaymentMethod.entries.map { method ->
            RecordingPaymentStrategy(
                method = method,
                events = paymentEvents,
                payFailure =
                    if (method == failingMethod) {
                        ErrorException(ErrorType.PAYMENT_DECLINED)
                    } else {
                        null
                    },
                cancelFailure =
                    if (method == cancelFailingMethod) {
                        IllegalStateException("cancel failed")
                    } else {
                        null
                    },
            )
        }
    val facade =
        BookingFacade(
            productService = productService,
            bookingReservationProcessor =
                BookingReservationProcessor(
                    productStockRepository = stockRepository,
                    orderRepository = orderRepository,
                    orderProductRepository = orderProductRepository,
                ),
            paymentService =
                PaymentService(
                    strategyRegistry = PaymentStrategyRegistry(strategies),
                    validator = PaymentValidator(),
                    paymentRepository = paymentRepository,
                    outboxEventRepository = outboxRepository,
                ),
            stockService =
                StockService(
                    productStockRepository = stockRepository,
                    stockCounterRepository = counterRepository,
                    distributedLock =
                        RecordingDistributedLock(
                            events =
                                if (recordLockEvents) {
                                    paymentEvents
                                } else {
                                    null
                                },
                        ),
                ),
        )
    return BookingFacadeFixture(
        facade = facade,
        orderRepository = orderRepository,
        orderProductRepository = orderProductRepository,
        stockRepository = stockRepository,
        counterRepository = counterRepository,
        paymentRepository = paymentRepository,
        outboxRepository = outboxRepository,
        paymentEvents = paymentEvents,
    )
}

fun bookingCommand(
    productOptionId: Long = 1L,
    userId: Long = 2L,
    totalAmount: Long = 100_000L,
    orderKey: String = "order-key",
    payments: List<PaymentCommand> = emptyList(),
): BookingCommand =
    BookingCommand(
        productOptionId = productOptionId,
        userId = userId,
        totalAmount = totalAmount,
        orderKey = orderKey,
        payments = payments,
    )
