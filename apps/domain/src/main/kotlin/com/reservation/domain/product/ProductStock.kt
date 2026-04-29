package com.reservation.domain.product

data class ProductStock(
    val productId: Long,
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val version: Long,
)
