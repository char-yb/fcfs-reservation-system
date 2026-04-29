package com.reservation.domain.user

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

data class UserPoint(
    val userId: Long,
    val pointBalance: Long,
) {
    fun deduct(amount: Long): UserPoint {
        if (pointBalance < amount) throw ErrorException(ErrorType.INSUFFICIENT_POINT)
        return copy(pointBalance = pointBalance - amount)
    }

    fun refund(amount: Long): UserPoint = copy(pointBalance = pointBalance + amount)
}
