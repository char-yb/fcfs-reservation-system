package com.reservation.domain.order

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class OrderProductTest :
    StringSpec({
        "주문 상품은 주문 당시 상품과 옵션 식별자를 기록한다" {
            val orderedAt = LocalDateTime.of(2026, 5, 2, 15, 0)

            val orderProduct =
                OrderProduct.ordered(
                    orderId = 1L,
                    productId = 10L,
                    productOptionId = 100L,
                    orderedAt = orderedAt,
                )

            orderProduct.orderProductId shouldBe 0L
            orderProduct.orderId shouldBe 1L
            orderProduct.productId shouldBe 10L
            orderProduct.productOptionId shouldBe 100L
            orderProduct.orderedAt shouldBe orderedAt
            orderProduct.confirmedAt shouldBe null
            orderProduct.canceledAt shouldBe null
        }
    })
