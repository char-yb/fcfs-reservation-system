package com.reservation.storage.rdb.user.point

import org.springframework.data.jpa.repository.JpaRepository

interface UserPointJpaRepository : JpaRepository<UserPointEntity, Long>
