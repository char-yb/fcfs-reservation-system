package com.reservation.application.booking

import com.reservation.application.fixture.bookingCommand
import com.reservation.application.fixture.bookingFacadeFixture
import com.reservation.application.payment.fixture.paymentCommand
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.outbox.OutboxEventType
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStatus
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
            val fixture = bookingFacadeFixture(events = events)

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
            fixture.paymentRepository.payments.map { it.status } shouldBe listOf(PaymentStatus.APPROVED, PaymentStatus.APPROVED)
            fixture.paymentEvents shouldBe
                listOf(
                    "stock:decrement",
                    "order:PENDING",
                    "pay:Y_POINT",
                    "pay:CREDIT_CARD",
                    "payment:APPROVED:Y_POINT",
                    "payment:APPROVED:CREDIT_CARD",
                    "order:CONFIRMED",
                )
        }

        "결제 실패 시 주문을 실패 처리하고 DB 재고와 Redis 카운터를 복구한다" {
            val fixture =
                bookingFacadeFixture(
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
            val fixture =
                bookingFacadeFixture(
                    counterDecrementFailure = RedisUnavailableException("redis down"),
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

        "주문 확정 실패 시 성공 결제를 보상하고 주문과 재고를 복구한다" {
            val events = mutableListOf<String>()
            val fixture =
                bookingFacadeFixture(
                    orderStatusFailures = mapOf(OrderStatus.CONFIRMED to ErrorException(ErrorType.INVALID_REQUEST)),
                    events = events,
                )

            val exception =
                shouldThrow<ErrorException> {
                    fixture.facade.booking(
                        bookingCommand(
                            payments =
                                listOf(
                                    paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                    paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                                ),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.INVALID_REQUEST
            fixture
                .orderRepository
                .orders
                .values
                .single()
                .status shouldBe OrderStatus.FAILED
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
            fixture.counterRepository.remaining shouldBe 2L
            fixture
                .paymentRepository
                .payments
                .map { it.status } shouldBe listOf(PaymentStatus.CANCELLED, PaymentStatus.CANCELLED)
            fixture.paymentEvents shouldBe
                listOf(
                    "stock:decrement",
                    "order:PENDING",
                    "pay:Y_POINT",
                    "pay:CREDIT_CARD",
                    "payment:APPROVED:Y_POINT",
                    "payment:APPROVED:CREDIT_CARD",
                    "cancel:CREDIT_CARD:tx_CREDIT_CARD",
                    "cancel:Y_POINT:tx_Y_POINT",
                    "payment:CANCELLED:Y_POINT",
                    "payment:CANCELLED:CREDIT_CARD",
                    "order:FAILED",
                    "stock:increment",
                )
        }

        "결제 저장 실패 시 성공 결제를 보상하고 주문과 재고를 복구한다" {
            val events = mutableListOf<String>()
            val fixture =
                bookingFacadeFixture(
                    paymentSaveFailure = IllegalStateException("payment save failed"),
                    events = events,
                )

            shouldThrow<IllegalStateException> {
                fixture.facade.booking(
                    bookingCommand(
                        payments =
                            listOf(
                                paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                            ),
                    ),
                )
            }

            fixture
                .orderRepository
                .orders
                .values
                .single()
                .status shouldBe OrderStatus.FAILED
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
            fixture.counterRepository.remaining shouldBe 2L
            fixture.paymentRepository.payments shouldBe emptyList()
            fixture.paymentEvents shouldBe
                listOf(
                    "stock:decrement",
                    "order:PENDING",
                    "pay:Y_POINT",
                    "pay:CREDIT_CARD",
                    "cancel:CREDIT_CARD:tx_CREDIT_CARD",
                    "cancel:Y_POINT:tx_Y_POINT",
                    "order:FAILED",
                    "stock:increment",
                )
        }

        "주문 확정 실패 후 결제 보상도 실패하면 outbox에 재처리 근거를 남긴다" {
            val fixture =
                bookingFacadeFixture(
                    orderStatusFailures = mapOf(OrderStatus.CONFIRMED to ErrorException(ErrorType.INVALID_REQUEST)),
                    cancelFailingMethod = PaymentMethod.Y_POINT,
                )

            shouldThrow<ErrorException> {
                fixture.facade.booking(
                    bookingCommand(
                        payments =
                            listOf(
                                paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000L),
                                paymentCommand(PaymentMethod.Y_POINT, amount = 30_000L),
                            ),
                    ),
                )
            }

            fixture
                .outboxRepository
                .events
                .single()
                .eventType shouldBe OutboxEventType.COMPENSATION_FAILURE
            fixture
                .outboxRepository
                .events
                .single()
                .orderId shouldBe 1L
            fixture
                .paymentRepository
                .payments
                .map { it.status } shouldBe listOf(PaymentStatus.APPROVED, PaymentStatus.CANCELLED)
            fixture.stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
        }
    })
