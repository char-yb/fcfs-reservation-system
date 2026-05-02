package com.reservation.domain.product

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.money.Money
import java.time.LocalDateTime

data class ProductOption(
    val id: Long,
    val productId: Long,
    val name: String,
    val price: Money,
    val saleOpenAt: LocalDateTime,
) {
    constructor(
        id: Long,
        productId: Long,
        name: String,
        price: Long,
        saleOpenAt: LocalDateTime,
    ) : this(
        id = id,
        productId = productId,
        name = name,
        price = Money(price),
        saleOpenAt = saleOpenAt,
    )

    fun validateSaleOpen(now: LocalDateTime = LocalDateTime.now()) {
        if (now.isBefore(saleOpenAt)) throw ErrorException(ErrorType.SALE_NOT_OPEN)
    }
}
