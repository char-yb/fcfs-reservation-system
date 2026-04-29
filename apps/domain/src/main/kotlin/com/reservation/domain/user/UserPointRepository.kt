package com.reservation.domain.user

interface UserPointRepository {
    fun findByUserId(userId: Long): UserPoint?

    fun save(userPoint: UserPoint): UserPoint
}
