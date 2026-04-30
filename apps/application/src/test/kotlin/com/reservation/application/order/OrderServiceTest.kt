package com.reservation.application.order

import com.reservation.application.fixture.FakeOrderRepository
import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderStatus
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrderServiceTest :
    StringSpec({
        "주문을 대기 상태로 생성한다" {
            val repository = FakeOrderRepository()
            val service = OrderService(repository)

            val order =
                service.create(
                    productId = 1L,
                    userId = 2L,
                    totalAmount = 100_000L,
                    orderKey = "order-key",
                )

            order.id shouldBe 1L
            order.status shouldBe OrderStatus.PENDING
            repository.findByOrderKey("order-key") shouldBe order
        }

        "같은 주문 키가 있으면 중복 요청으로 거부한다" {
            val repository =
                FakeOrderRepository(
                    listOf(
                        Order(
                            id = 1L,
                            productId = 1L,
                            userId = 2L,
                            totalAmount = 100_000L,
                            status = OrderStatus.PENDING,
                            orderKey = "order-key",
                        ),
                    ),
                )
            val service = OrderService(repository)

            val exception =
                shouldThrow<ErrorException> {
                    service.create(
                        productId = 1L,
                        userId = 2L,
                        totalAmount = 100_000L,
                        orderKey = "order-key",
                    )
                }

            exception.errorType shouldBe ErrorType.DUPLICATE_REQUEST
        }

        "주문을 확정 상태로 변경한다" {
            val repository =
                FakeOrderRepository(
                    listOf(
                        Order(
                            id = 1L,
                            productId = 1L,
                            userId = 2L,
                            totalAmount = 100_000L,
                            status = OrderStatus.PENDING,
                            orderKey = "order-key",
                        ),
                    ),
                )
            val service = OrderService(repository)

            service.confirm(1L).status shouldBe OrderStatus.CONFIRMED
        }

        "주문을 실패 상태로 변경한다" {
            val repository =
                FakeOrderRepository(
                    listOf(
                        Order(
                            id = 1L,
                            productId = 1L,
                            userId = 2L,
                            totalAmount = 100_000L,
                            status = OrderStatus.PENDING,
                            orderKey = "order-key",
                        ),
                    ),
                )
            val service = OrderService(repository)

            service.fail(1L).status shouldBe OrderStatus.FAILED
        }
    })
