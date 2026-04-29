package com.reservation.api.booking

import com.reservation.api.booking.request.BookingRequest
import com.reservation.api.booking.response.BookingResponse
import com.reservation.api.response.ApiResponse
import com.reservation.application.booking.BookingFacade
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/booking")
class BookingController(
    private val bookingFacade: BookingFacade,
) {
    @PostMapping("/{userId}")
    fun book(
        @PathVariable userId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: BookingRequest,
    ): ApiResponse<BookingResponse> {
        validateIdempotencyKey(idempotencyKey)
        val result = bookingFacade.book(request.toCommand(userId = userId, orderKey = idempotencyKey))
        return ApiResponse.success(BookingResponse.from(result))
    }

    private fun validateIdempotencyKey(key: String) {
        runCatching { UUID.fromString(key) }
            .onFailure { throw ErrorException(ErrorType.IDEMPOTENCY_KEY_INVALID) }
    }
}
