package com.reservation.api.user.request

import jakarta.validation.constraints.Positive

data class PointChargeRequest(
    @field:Positive val amount: Long,
)
