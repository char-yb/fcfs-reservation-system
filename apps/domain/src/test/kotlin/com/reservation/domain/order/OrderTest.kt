package com.reservation.domain.order

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrderTest :
    StringSpec({
        "대기 주문은 결제 완료 상태로 전환된다" {
            val order =
                Order(
                    id = 1L,
                    userId = 20L,
                    totalAmount = 100_000L,
                    status = OrderStatus.PENDING,
                    orderKey = "order-key",
                )

            order.transitionTo(OrderStatus.PAID).status shouldBe OrderStatus.PAID
        }

        "결제 완료 주문은 확정 상태로 전환된다" {
            val order =
                Order(
                    id = 1L,
                    userId = 20L,
                    totalAmount = 100_000L,
                    status = OrderStatus.PAID,
                    orderKey = "order-key",
                )

            order.transitionTo(OrderStatus.CONFIRMED).status shouldBe OrderStatus.CONFIRMED
        }

        "대기 주문은 확정 상태로 전환된다" {
            val order =
                Order(
                    id = 1L,
                    userId = 20L,
                    totalAmount = 100_000L,
                    status = OrderStatus.PENDING,
                    orderKey = "order-key",
                )

            order.transitionTo(OrderStatus.CONFIRMED).status shouldBe OrderStatus.CONFIRMED
        }

        "허용되지 않은 주문 상태 전환은 거부한다" {
            val order =
                Order(
                    id = 1L,
                    userId = 20L,
                    totalAmount = 100_000L,
                    status = OrderStatus.CONFIRMED,
                    orderKey = "order-key",
                )

            val exception =
                shouldThrow<ErrorException> {
                    order.transitionTo(OrderStatus.FAILED)
                }

            exception.errorType shouldBe ErrorType.INVALID_ORDER_STATUS_TRANSITION
        }
    })
