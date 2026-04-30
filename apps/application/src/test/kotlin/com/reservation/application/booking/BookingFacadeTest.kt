package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.fixture.FakeOrderRepository
import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.bookingFacade
import com.reservation.application.payment.fixture.PaymentTestFixture.command
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.product.ProductStock
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BookingFacadeTest :
    StringSpec({
        "예약 성공 시 재고를 차감하고 주문을 확정한다" {
            val orderRepository = FakeOrderRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(ProductStock(productId = 1L, totalQuantity = 10, remainingQuantity = 1, version = 0L)),
                )
            val facade =
                bookingFacade(
                    orderRepository = orderRepository,
                    stockRepository = stockRepository,
                )

            val result =
                facade.booking(
                    BookingCommand(
                        productId = 1L,
                        userId = 2L,
                        totalAmount = 100_000L,
                        orderKey = "order-key",
                        payments =
                            listOf(
                                command(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                command(PaymentMethod.Y_POINT, amount = 30_000L),
                            ),
                    ),
                )

            result.status shouldBe OrderStatus.CONFIRMED
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 0
            orderRepository.orders[result.orderId]?.status shouldBe OrderStatus.CONFIRMED
        }

        "결제 실패 시 주문을 실패 처리하고 DB 재고와 Redis 카운터를 복구한다" {
            val orderRepository = FakeOrderRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(ProductStock(productId = 1L, totalQuantity = 10, remainingQuantity = 1, version = 0L)),
                )
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val facade =
                bookingFacade(
                    orderRepository = orderRepository,
                    stockRepository = stockRepository,
                    counterRepository = counterRepository,
                    failingMethod = PaymentMethod.Y_PAY,
                )

            val exception =
                shouldThrow<ErrorException> {
                    facade.booking(
                        BookingCommand(
                            productId = 1L,
                            userId = 2L,
                            totalAmount = 100_000L,
                            orderKey = "order-key",
                            payments =
                                listOf(
                                    command(PaymentMethod.Y_PAY, amount = 70_000L),
                                    command(PaymentMethod.Y_POINT, amount = 30_000L),
                                ),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_DECLINED
            orderRepository
                .orders
                .values
                .single()
                .status shouldBe OrderStatus.FAILED
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
            counterRepository.remaining shouldBe 2L
        }
    })
