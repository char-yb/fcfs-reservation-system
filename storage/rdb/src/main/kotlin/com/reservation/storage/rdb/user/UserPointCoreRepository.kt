package com.reservation.storage.rdb.user

import com.reservation.domain.user.UserPoint
import com.reservation.domain.user.UserPointRepository
import org.springframework.stereotype.Repository

@Repository
class UserPointCoreRepository(
    private val jpaRepository: UserPointJpaRepository,
) : UserPointRepository {
    override fun findByUserId(userId: Long): UserPoint? = jpaRepository.findById(userId).orElse(null)?.toDomain()

    override fun save(userPoint: UserPoint): UserPoint {
        val entity =
            jpaRepository
                .findById(userPoint.userId)
                .orElse(null)
                ?.also { it.pointBalance = userPoint.pointBalance }
                ?: UserPointEntity(userId = userPoint.userId, pointBalance = userPoint.pointBalance)
        return jpaRepository.save(entity).toDomain()
    }
}
