package com.reservation.storage.rdb.user

import com.reservation.domain.user.User
import com.reservation.domain.user.UserRepository
import org.springframework.stereotype.Repository

@Repository
class UserCoreRepository(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findById(id: Long): User? = jpaRepository.findById(id).orElse(null)?.toDomain()
}
