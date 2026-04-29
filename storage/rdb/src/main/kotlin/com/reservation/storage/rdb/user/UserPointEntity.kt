package com.reservation.storage.rdb.user

import com.reservation.domain.user.UserPoint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_points")
class UserPointEntity(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "point_balance", nullable = false)
    var pointBalance: Long,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): UserPoint =
        UserPoint(
            userId = userId,
            pointBalance = pointBalance,
        )
}
