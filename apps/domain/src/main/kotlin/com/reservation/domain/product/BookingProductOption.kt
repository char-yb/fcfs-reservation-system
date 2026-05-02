package com.reservation.domain.product

import com.reservation.support.money.Money
import java.time.LocalDateTime

data class BookingProductOption(
    val product: Product,
    val option: ProductOption,
    val schedule: BookingSchedule,
) {
    val id: Long
        get() = option.id

    val productId: Long
        get() = product.id

    val productName: String
        get() = product.name

    val productType: ProductType
        get() = product.type

    val optionName: String
        get() = option.name

    val price: Money
        get() = option.price

    fun validateSaleOpen(now: LocalDateTime = LocalDateTime.now()) {
        option.validateSaleOpen(now)
    }
}
