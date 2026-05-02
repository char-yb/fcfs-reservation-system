package com.reservation.application.fixture

import com.reservation.domain.user.UserPoint
import com.reservation.domain.user.UserPointRepository

class FakeUserPointRepository(
    initialPoints: List<UserPoint> = emptyList(),
) : UserPointRepository {
    val points: MutableMap<Long, UserPoint> = initialPoints.associateBy { it.userId }.toMutableMap()

    override fun findByUserId(userId: Long): UserPoint? = points[userId]

    override fun save(userPoint: UserPoint): UserPoint {
        points[userPoint.userId] = userPoint
        return userPoint
    }

    override fun deductIfEnough(
        userId: Long,
        amount: Long,
    ): Boolean {
        val userPoint = points[userId] ?: return false
        if (userPoint.pointBalance < amount) return false
        points[userId] = userPoint.copy(pointBalance = userPoint.pointBalance - amount)
        return true
    }

    override fun increase(
        userId: Long,
        amount: Long,
    ): Boolean {
        val userPoint = points[userId] ?: return false
        points[userId] = userPoint.copy(pointBalance = userPoint.pointBalance + amount)
        return true
    }
}
