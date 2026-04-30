package com.reservation.domain.product

import java.time.LocalDateTime

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: Long,
    val checkInAt: LocalDateTime,
    val checkOutAt: LocalDateTime,
    val remainingQuantity: Int
)
