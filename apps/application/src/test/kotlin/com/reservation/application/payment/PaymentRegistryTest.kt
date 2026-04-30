package com.reservation.application.payment

import com.reservation.application.fixture.RecordingPaymentGateway
import com.reservation.application.payment.fixture.RecordingPaymentStrategy
import com.reservation.domain.payment.PaymentMethod
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentRegistryTest :
    StringSpec({
        "결제 전략 레지스트리는 결제 수단에 맞는 전략을 반환한다" {
            val strategies =
                PaymentMethod.entries.map { method ->
                    RecordingPaymentStrategy(method = method, events = mutableListOf())
                }
            val registry = PaymentStrategyRegistry(strategies)

            registry.get(PaymentMethod.Y_POINT).method shouldBe PaymentMethod.Y_POINT
        }

        "결제 전략이 누락되면 레지스트리 생성을 거부한다" {
            val strategies =
                listOf(
                    RecordingPaymentStrategy(method = PaymentMethod.CREDIT_CARD, events = mutableListOf()),
                    RecordingPaymentStrategy(method = PaymentMethod.Y_PAY, events = mutableListOf()),
                )

            shouldThrow<IllegalArgumentException> {
                PaymentStrategyRegistry(strategies)
            }
        }

        "PG 레지스트리는 결제 수단에 맞는 게이트웨이를 반환한다" {
            val gateway = RecordingPaymentGateway(PaymentMethod.CREDIT_CARD)
            val registry = PgGatewayRegistry(listOf(gateway))

            registry.get(PaymentMethod.CREDIT_CARD) shouldBe gateway
        }

        "PG 게이트웨이가 누락되면 조회를 거부한다" {
            val registry = PgGatewayRegistry(emptyList())

            shouldThrow<IllegalStateException> {
                registry.get(PaymentMethod.CREDIT_CARD)
            }
        }
    })
