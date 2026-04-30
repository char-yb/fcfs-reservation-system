package com.reservation.application.payment

import com.reservation.application.payment.fixture.PaymentTestFixture.command
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentValidatorTest :
    StringSpec({
        val validator = PaymentValidator()

        "신용카드와 Y 포인트 조합은 허용한다" {
            val commands =
                listOf(
                    command(PaymentMethod.CREDIT_CARD, amount = 70_000),
                    command(PaymentMethod.Y_POINT, amount = 30_000),
                )

            validator.validate(commands)
            validator.validateTotal(commands, expectedTotal = 100_000)
        }

        "Y 페이와 Y 포인트 조합은 허용한다" {
            val commands =
                listOf(
                    command(PaymentMethod.Y_PAY, amount = 70_000),
                    command(PaymentMethod.Y_POINT, amount = 30_000),
                )

            validator.validate(commands)
            validator.validateTotal(commands, expectedTotal = 100_000)
        }

        "신용카드와 Y 페이 조합은 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(
                        listOf(
                            command(PaymentMethod.CREDIT_CARD),
                            command(PaymentMethod.Y_PAY),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "동일한 결제 수단 중복은 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(
                        listOf(
                            command(PaymentMethod.Y_POINT),
                            command(PaymentMethod.Y_POINT),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "0 이하 결제 금액은 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(listOf(command(PaymentMethod.Y_POINT, amount = 0)))
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "결제 합계가 주문 금액과 다르면 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validateTotal(
                        commands = listOf(command(PaymentMethod.Y_POINT, amount = 10_000)),
                        expectedTotal = 20_000,
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }
    })
