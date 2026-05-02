package com.reservation.storage.rdb.product.booking

import org.springframework.data.jpa.repository.JpaRepository

interface BookingScheduleJpaRepository : JpaRepository<BookingScheduleEntity, Long> {
    fun findByProductOptionId(productOptionId: Long): BookingScheduleEntity?
}
