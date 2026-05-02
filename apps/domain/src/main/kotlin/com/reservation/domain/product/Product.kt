package com.reservation.domain.product

data class Product(
    val id: Long,
    val name: String,
    val type: ProductType,
)
