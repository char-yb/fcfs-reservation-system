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
}
