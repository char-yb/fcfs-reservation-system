package com.reservation.domain.user

interface UserRepository {
    fun findById(id: Long): User?
}
