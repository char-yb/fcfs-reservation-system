package com.reservation.application.payment

import com.reservation.application.payment.fixture.paymentCommand
import com.reservation.application.payment.fixture.paymentServiceFixture
import com.reservation.domain.outbox.CompensationFailurePayload
import com.reservation.domain.outbox.OutboxEventType
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.money.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentServiceTest :
    StringSpec({
        "Y 포인트는 외부 PG 결제보다 먼저 실행한다" {
            val fixture = paymentServiceFixture()
            val service = fixture.service

            val results =
                service.execute(
                    commands =
                        listOf(
                            paymentCommand(PaymentMethod.Y_PAY, amount = 70_000),
                            paymentCommand(PaymentMethod.Y_POINT, amount = 30_000),
                        ),
                    totalAmount = Money(100_000L),
                )

            fixture.events shouldBe listOf("pay:Y_POINT", "pay:Y_PAY")
            results.map { it.method } shouldBe listOf(PaymentMethod.Y_POINT, PaymentMethod.Y_PAY)
        }

        "Y 포인트 성공 후 Y 페이가 실패하면 Y 포인트를 취소한다" {
            val fixture = paymentServiceFixture(failingMethod = PaymentMethod.Y_PAY)
            val service = fixture.service

            val exception =
                shouldThrow<ErrorException> {
                    service.execute(
                        commands =
                            listOf(
                                paymentCommand(PaymentMethod.Y_PAY, amount = 70_000),
                                paymentCommand(PaymentMethod.Y_POINT, amount = 30_000),
                            ),
                        totalAmount = Money(100_000L),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_DECLINED
            fixture.events shouldBe listOf("pay:Y_POINT", "pay:Y_PAY", "cancel:Y_POINT:tx_Y_POINT")
        }

        "보상 처리는 성공한 결제의 역순으로 실행한다" {
            val fixture = paymentServiceFixture()
            val service = fixture.service

            service.compensate(
                orderId = 1L,
                results =
                    listOf(
                        PaymentExecutionResult(PaymentMethod.Y_POINT, amount = 30_000, transactionId = "tx_point"),
                        PaymentExecutionResult(PaymentMethod.CREDIT_CARD, amount = 70_000, transactionId = "tx_card"),
                    ),
            )

            fixture.events shouldBe listOf("cancel:CREDIT_CARD:tx_card", "cancel:Y_POINT:tx_point")
        }

        "보상 실패는 outbox에 COMPENSATION_FAILURE로 기록한다" {
            val fixture = paymentServiceFixture(cancelFailingMethod = PaymentMethod.Y_POINT)
            val result = PaymentExecutionResult(PaymentMethod.Y_POINT, amount = 30_000, transactionId = "tx_point")

            val failures = fixture.service.compensate(orderId = 10L, results = listOf(result))

            failures shouldBe listOf(result)
            fixture
                .outboxRepository
                .events
                .single()
                .orderId shouldBe 10L
            fixture
                .outboxRepository
                .events
                .single()
                .eventType shouldBe OutboxEventType.COMPENSATION_FAILURE
            fixture
                .outboxRepository
                .events
                .single()
                .payload shouldBe
                CompensationFailurePayload(
                    orderId = 10L,
                    method = PaymentMethod.Y_POINT,
                    amount = 30_000,
                    pgTransactionId = "tx_point",
                    reason = "cancel failed",
                )
        }
    })
