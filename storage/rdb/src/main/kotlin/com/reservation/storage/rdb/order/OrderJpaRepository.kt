package com.reservation.storage.rdb.order

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByOrderKey(orderKey: String): Optional<OrderEntity>
}
