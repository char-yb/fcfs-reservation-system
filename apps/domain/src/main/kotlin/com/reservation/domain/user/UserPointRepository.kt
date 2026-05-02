package com.reservation.domain.user

interface UserPointRepository {
    fun findByUserId(userId: Long): UserPoint?

    fun save(userPoint: UserPoint): UserPoint

    fun deductIfEnough(
        userId: Long,
        amount: Long,
    ): Boolean

    fun increase(
        userId: Long,
        amount: Long,
    ): Boolean
}
