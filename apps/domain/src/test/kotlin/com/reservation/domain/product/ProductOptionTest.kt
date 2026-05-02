package com.reservation.domain.product

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class ProductOptionTest :
    StringSpec({
        "판매 시작 시간이 지나면 예약을 허용한다" {
            val now = LocalDateTime.of(2026, 4, 30, 10, 0)
            val option =
                ProductOption(
                    id = 1L,
                    productId = 1L,
                    name = "standard",
                    price = 100_000L,
                    saleOpenAt = now.minusMinutes(1),
                )

            option.validateSaleOpen(now)
        }

        "판매 시작 전이면 예약을 거부한다" {
            val now = LocalDateTime.of(2026, 4, 30, 10, 0)
            val option =
                ProductOption(
                    id = 1L,
                    productId = 1L,
                    name = "standard",
                    price = 100_000L,
                    saleOpenAt = now.plusMinutes(1),
                )

            val exception =
                shouldThrow<ErrorException> {
                    option.validateSaleOpen(now)
                }

            exception.errorType shouldBe ErrorType.SALE_NOT_OPEN
        }
    })
