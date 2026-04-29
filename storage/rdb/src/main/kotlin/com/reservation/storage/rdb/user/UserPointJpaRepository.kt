package com.reservation.storage.rdb.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserPointJpaRepository : JpaRepository<UserPointEntity, Long>
