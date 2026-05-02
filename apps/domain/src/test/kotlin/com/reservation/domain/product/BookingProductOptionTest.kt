package com.reservation.domain.product

import com.reservation.support.money.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class BookingProductOptionTest :
    StringSpec({
        "예약 상품 옵션은 상품 메타, 옵션 메타, 예약 일정을 묶어 제공한다" {
            val now = LocalDateTime.of(2026, 4, 30, 10, 0)
            val product =
                Product(
                    id = 1L,
                    name = "room",
                    type = ProductType.BOOKING,
                )
            val option =
                ProductOption(
                    id = 10L,
                    productId = product.id,
                    name = "standard",
                    price = 100_000L,
                    saleOpenAt = now.minusMinutes(1),
                )
            val schedule =
                BookingSchedule(
                    checkInAt = now.plusDays(1),
                    checkOutAt = now.plusDays(2),
                )

            val bookingOption =
                BookingProductOption(
                    product = product,
                    option = option,
                    schedule = schedule,
                )
            val stock =
                ProductStock(
                    productOptionId = bookingOption.id,
                    totalQuantity = 10,
                    remainingQuantity = 7,
                )
            bookingOption.productId shouldBe 1L
            bookingOption.productName shouldBe "room"
            bookingOption.productType shouldBe ProductType.BOOKING
            bookingOption.optionName shouldBe "standard"
            bookingOption.price shouldBe Money(100_000L)
            bookingOption.schedule.checkInAt shouldBe now.plusDays(1)
            stock.remainingQuantity shouldBe 7
        }
    })
