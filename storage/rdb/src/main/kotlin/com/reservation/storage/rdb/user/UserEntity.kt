package com.reservation.storage.rdb.user

import com.reservation.domain.user.User
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
) : BaseEntity() {

    fun toDomain(): User = User(
        id = id,
        name = name,
    )
}
