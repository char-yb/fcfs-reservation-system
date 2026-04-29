package com.reservation.domain.product

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import java.time.LocalDateTime

data class Product(
    val id: Long,
    val name: String,
    val price: Long,
    val checkInAt: LocalDateTime,
    val checkOutAt: LocalDateTime,
    val saleOpenAt: LocalDateTime,
) {
    fun validateSaleOpen(now: LocalDateTime = LocalDateTime.now()) {
        if (now.isBefore(saleOpenAt)) throw ErrorException(ErrorType.SALE_NOT_OPEN)
    }
}
