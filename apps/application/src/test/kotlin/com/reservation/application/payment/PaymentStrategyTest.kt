package com.reservation.application.payment

import com.reservation.application.fixture.FakeUserPointRepository
import com.reservation.application.fixture.RecordingPaymentGateway
import com.reservation.application.payment.credit.CreditCardPaymentStrategy
import com.reservation.application.payment.ypay.YPayPaymentStrategy
import com.reservation.application.user.UserPointService
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.user.UserPoint
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentStrategyTest :
    StringSpec({
        "신용카드는 카드 토큰으로 PG 결제를 요청한다" {
            val gateway = RecordingPaymentGateway(PaymentMethod.CREDIT_CARD)
            val strategy = CreditCardPaymentStrategy(PgGatewayRegistry(listOf(gateway)))

            val result =
                strategy.pay(
                    PaymentCommand(
                        method = PaymentMethod.CREDIT_CARD,
                        amount = 70_000L,
                        userId = 1L,
                        attributes = mapOf("cardToken" to "card-token"),
                    ),
                )

            result.method shouldBe PaymentMethod.CREDIT_CARD
            gateway.chargeRequests.single().method shouldBe "CARD"
            gateway.chargeRequests.single().amount shouldBe 70_000L
            gateway.chargeRequests.single().token shouldBe "card-token"
        }

        "신용카드 토큰이 없으면 결제를 거부한다" {
            val strategy = CreditCardPaymentStrategy(PgGatewayRegistry(listOf(RecordingPaymentGateway(PaymentMethod.CREDIT_CARD))))

            val exception =
                shouldThrow<ErrorException> {
                    strategy.pay(PaymentCommand(method = PaymentMethod.CREDIT_CARD, amount = 70_000L, userId = 1L))
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "Y 페이는 페이 토큰으로 PG 결제를 요청한다" {
            val gateway = RecordingPaymentGateway(PaymentMethod.Y_PAY)
            val strategy = YPayPaymentStrategy(PgGatewayRegistry(listOf(gateway)))

            val result =
                strategy.pay(
                    PaymentCommand(
                        method = PaymentMethod.Y_PAY,
                        amount = 70_000L,
                        userId = 1L,
                        attributes = mapOf("payToken" to "pay-token"),
                    ),
                )

            result.method shouldBe PaymentMethod.Y_PAY
            gateway.chargeRequests.single().method shouldBe "Y_PAY"
            gateway.chargeRequests.single().amount shouldBe 70_000L
            gateway.chargeRequests.single().token shouldBe "pay-token"
        }

        "Y 페이 토큰이 없으면 결제를 거부한다" {
            val strategy = YPayPaymentStrategy(PgGatewayRegistry(listOf(RecordingPaymentGateway(PaymentMethod.Y_PAY))))

            val exception =
                shouldThrow<ErrorException> {
                    strategy.pay(PaymentCommand(method = PaymentMethod.Y_PAY, amount = 70_000L, userId = 1L))
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "Y 포인트는 서버가 주입한 사용자 식별자로 포인트를 차감한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 30_000L)))
            val strategy = YPointPaymentStrategy(UserPointService(repository))

            val result = strategy.pay(PaymentCommand(method = PaymentMethod.Y_POINT, amount = 20_000L, userId = 1L))

            result.method shouldBe PaymentMethod.Y_POINT
            repository.points[1L]?.pointBalance shouldBe 10_000L
        }

        "Y 포인트 취소는 거래 식별자를 기준으로 환불한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 10_000L)))
            val strategy = YPointPaymentStrategy(UserPointService(repository))

            val result = strategy.cancel("pt_1_20000_uuid")

            result.method shouldBe PaymentMethod.Y_POINT
            repository.points[1L]?.pointBalance shouldBe 30_000L
        }
    })
