package com.reservation.storage.rdb.user.point

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserPointJpaRepository : JpaRepository<UserPointEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE UserPointEntity p
        SET p.pointBalance = p.pointBalance - :amount, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.userId = :userId AND p.pointBalance >= :amount
        """,
    )
    fun deductIfEnough(
        userId: Long,
        amount: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE UserPointEntity p
        SET p.pointBalance = p.pointBalance + :amount, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.userId = :userId
        """,
    )
    fun increase(
        userId: Long,
        amount: Long,
    ): Int
}
