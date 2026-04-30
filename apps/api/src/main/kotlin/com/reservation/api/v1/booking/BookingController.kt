package com.reservation.api.v1.booking

import com.reservation.api.v1.booking.request.BookingRequest
import com.reservation.api.v1.booking.response.BookingResponse
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
@RequestMapping("/api/v1/booking")
class BookingController(
    private val bookingFacade: BookingFacade,
) {
    @PostMapping("/{userId}")
    fun booking(
        @PathVariable userId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,   // 멱등성 보장을 위한 주문 키
        @Valid @RequestBody request: BookingRequest,
    ): ApiResponse<BookingResponse> {
        runCatching { UUID.fromString(idempotencyKey) }
            .onFailure { throw ErrorException(ErrorType.IDEMPOTENCY_KEY_INVALID) }
        val result = bookingFacade.booking(request.toCommand(userId = userId, orderKey = idempotencyKey))
        return ApiResponse.success(BookingResponse.from(result))
    }
}
