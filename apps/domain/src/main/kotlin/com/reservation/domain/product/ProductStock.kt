package com.reservation.domain.product

data class ProductStock(
    val productOptionId: Long,
    val totalQuantity: Int,
    val remainingQuantity: Int,
)
