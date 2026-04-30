package com.reservation.api.v1.user

import com.reservation.api.response.ApiResponse
import com.reservation.api.v1.user.request.PointChargeRequest
import com.reservation.api.v1.user.response.PointChargeResponse
import com.reservation.application.user.UserPointService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserPointController(
    private val userPointService: UserPointService,
) {
    @PostMapping("/{userId}/points/charge")
    fun charge(
        @PathVariable userId: Long,
        @Valid @RequestBody request: PointChargeRequest,
    ): ApiResponse<PointChargeResponse> {
        val result = userPointService.charge(userId = userId, amount = request.amount)
        return ApiResponse.success(PointChargeResponse(userId = result.userId, pointBalance = result.pointBalance))
    }
}
