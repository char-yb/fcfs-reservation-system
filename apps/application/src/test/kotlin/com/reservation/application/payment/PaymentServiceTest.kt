package com.reservation.application.payment

import com.reservation.application.payment.fixture.PaymentTestFixture.command
import com.reservation.application.payment.fixture.PaymentTestFixture.paymentService
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentServiceTest :
    StringSpec({
        "Y 포인트는 외부 PG 결제보다 먼저 실행한다" {
            val events = mutableListOf<String>()
            val service = paymentService(events)

            val results =
                service.execute(
                    commands =
                        listOf(
                            command(PaymentMethod.Y_PAY, amount = 70_000),
                            command(PaymentMethod.Y_POINT, amount = 30_000),
                        ),
                    totalAmount = 100_000,
                )

            events shouldBe listOf("pay:Y_POINT", "pay:Y_PAY")
            results.map { it.method } shouldBe listOf(PaymentMethod.Y_POINT, PaymentMethod.Y_PAY)
        }

        "Y 포인트 성공 후 Y 페이가 실패하면 Y 포인트를 취소한다" {
            val events = mutableListOf<String>()
            val service = paymentService(events, failingMethod = PaymentMethod.Y_PAY)

            val exception =
                shouldThrow<ErrorException> {
                    service.execute(
                        commands =
                            listOf(
                                command(PaymentMethod.Y_PAY, amount = 70_000),
                                command(PaymentMethod.Y_POINT, amount = 30_000),
                            ),
                        totalAmount = 100_000,
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_DECLINED
            events shouldBe listOf("pay:Y_POINT", "pay:Y_PAY", "cancel:Y_POINT:tx_Y_POINT")
        }

        "보상 처리는 성공한 결제의 역순으로 실행한다" {
            val events = mutableListOf<String>()
            val service = paymentService(events)
            val compensate =
                PaymentService::class.java
                    .getDeclaredMethod("compensate", List::class.java)
                    .apply { isAccessible = true }

            compensate.invoke(
                service,
                listOf(
                    PaymentExecutionResult(PaymentMethod.Y_POINT, amount = 30_000, transactionId = "tx_point"),
                    PaymentExecutionResult(PaymentMethod.CREDIT_CARD, amount = 70_000, transactionId = "tx_card"),
                ),
            )

            events shouldBe listOf("cancel:CREDIT_CARD:tx_card", "cancel:Y_POINT:tx_point")
        }
    })
