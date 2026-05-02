package com.reservation.support.money

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class MoneyTest :
    StringSpec({
        "금액 동등성은 BigDecimal scale 차이에 영향을 받지 않는다" {
            Money(BigDecimal("100000.00")) shouldBe Money(100_000L)
        }

        "금액 연산은 Money로 반환한다" {
            (Money(70_000L) + Money(30_000L)) shouldBe Money(100_000L)
            (Money(100_000L) - Money(30_000L)) shouldBe Money(70_000L)
        }
    })
