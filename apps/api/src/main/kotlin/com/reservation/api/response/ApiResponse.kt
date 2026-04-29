package com.reservation.api.response

import java.time.LocalDateTime

data class ApiResponse<T>(
    val success: Boolean,
    val status: Int,
    val data: T? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, status = 200, data = data)

        fun <T> success(
            status: Int,
            data: T,
        ): ApiResponse<T> = ApiResponse(success = true, status = status, data = data)

        fun fail(
            status: Int,
            errorResponse: ErrorResponse,
        ): ApiResponse<ErrorResponse> = ApiResponse(success = false, status = status, data = errorResponse)
    }
}
