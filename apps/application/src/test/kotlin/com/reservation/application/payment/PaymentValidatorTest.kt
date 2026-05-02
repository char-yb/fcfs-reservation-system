package com.reservation.application.payment

import com.reservation.application.payment.fixture.paymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.money.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentValidatorTest :
    StringSpec({
        val validator = PaymentValidator()

        "신용카드와 Y 포인트 조합은 허용한다" {
            val commands =
                listOf(
                    paymentCommand(PaymentMethod.CREDIT_CARD, amount = 70_000),
                    paymentCommand(PaymentMethod.Y_POINT, amount = 30_000),
                )

            validator.validate(commands)
            validator.validateTotal(commands, expectedTotal = Money(100_000L))
        }

        "Y 페이와 Y 포인트 조합은 허용한다" {
            val commands =
                listOf(
                    paymentCommand(PaymentMethod.Y_PAY, amount = 70_000),
                    paymentCommand(PaymentMethod.Y_POINT, amount = 30_000),
                )

            validator.validate(commands)
            validator.validateTotal(commands, expectedTotal = Money(100_000L))
        }

        "신용카드와 Y 페이 조합은 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(
                        listOf(
                            paymentCommand(PaymentMethod.CREDIT_CARD),
                            paymentCommand(PaymentMethod.Y_PAY),
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
                            paymentCommand(PaymentMethod.Y_POINT),
                            paymentCommand(PaymentMethod.Y_POINT),
                        ),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "0 이하 결제 금액은 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(listOf(paymentCommand(PaymentMethod.Y_POINT, amount = 0)))
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "결제 수단이 비어있으면 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validate(emptyList())
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }

        "결제 합계가 주문 금액과 다르면 거부한다" {
            val exception =
                shouldThrow<ErrorException> {
                    validator.validateTotal(
                        commands = listOf(paymentCommand(PaymentMethod.Y_POINT, amount = 10_000)),
                        expectedTotal = Money(20_000L),
                    )
                }

            exception.errorType shouldBe ErrorType.PAYMENT_METHOD_INVALID
        }
    })
