package com.reservation.application.booking

import com.reservation.application.fixture.FakeOrderRepository
import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.bookingCommand
import com.reservation.application.fixture.bookingFacadeFixture
import com.reservation.application.fixture.productStock
import com.reservation.application.payment.fixture.paymentCommand
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.redis.RedisUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BookingFacadeTest :
    StringSpec({
        "예약 성공 시 재고를 차감하고 주문을 확정한다" {
            val events = mutableListOf<String>()
            val orderRepository = FakeOrderRepository(events = events)
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 1)),
                    events = events,
                )
            val fixture =
                bookingFacadeFixture(
                    orderRepository = orderRepository,
                    stockRepository = stockRepository,
                    paymentEvents = events,
                )

            val result =
                fixture.facade.booking(
                    bookingCommand(
                        payments =
                            listOf(
                                paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                            ),
                    ),
                )

            result.status shouldBe OrderStatus.CONFIRMED
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 0
            fixture.orderRepository.orders[result.orderId]?.status shouldBe OrderStatus.CONFIRMED
            fixture.paymentEvents shouldBe
                listOf("stock:decrement", "order:PENDING", "pay:Y_POINT", "pay:CREDIT_CARD", "order:CONFIRMED")
        }

        "결제 실패 시 주문을 실패 처리하고 DB 재고와 Redis 카운터를 복구한다" {
            val orderRepository = FakeOrderRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 1)),
                )
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val fixture =
                bookingFacadeFixture(
                    orderRepository = orderRepository,
                    stockRepository = stockRepository,
                    counterRepository = counterRepository,
                    failingMethod = PaymentMethod.Y_PAY,
                )

            val exception =
                shouldThrow<ErrorException> {
                    fixture.facade.booking(
                        bookingCommand(
                            payments =
                                listOf(
                                    paymentCommand(PaymentMethod.Y_PAY, amount = 70_000L),
                                    paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                                ),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_DECLINED
            fixture
                .orderRepository
                .orders
                .values
                .single()
                .status shouldBe OrderStatus.FAILED
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
            fixture.counterRepository.remaining shouldBe 2L
        }

        "Redis 장애 fallback에서도 DB 예약으로 주문을 확정한다" {
            val orderRepository = FakeOrderRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 1)),
                )
            val counterRepository =
                FakeStockCounterRepository(
                    initialRemaining = 2L,
                    decrementFailure = RedisUnavailableException("redis down"),
                )
            val fixture =
                bookingFacadeFixture(
                    orderRepository = orderRepository,
                    stockRepository = stockRepository,
                    counterRepository = counterRepository,
                )

            val result =
                fixture.facade.booking(
                    bookingCommand(
                        payments =
                            listOf(
                                paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                            ),
                    ),
                )

            result.status shouldBe OrderStatus.CONFIRMED
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 0
            fixture.counterRepository.remaining shouldBe 2L
            fixture.counterRepository.incrementCalls shouldBe 0
        }
    })
