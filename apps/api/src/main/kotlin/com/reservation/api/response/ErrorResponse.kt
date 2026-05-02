package com.reservation.api.response

data class ErrorResponse(
    val code: String,
    val message: String,
    val orderId: Long? = null,
)
