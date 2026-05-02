package com.reservation.storage.rdb.product.booking

import com.reservation.domain.product.BookingSchedule
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "booking_schedules",
    uniqueConstraints = [UniqueConstraint(name = "uk_booking_schedules_product_option_id", columnNames = ["product_option_id"])],
)
class BookingScheduleEntity(
    @Column(name = "product_option_id", nullable = false)
    val productOptionId: Long,
    @Column(name = "check_in_at", nullable = false)
    val checkInAt: LocalDateTime,
    @Column(name = "check_out_at", nullable = false)
    val checkOutAt: LocalDateTime,
) : BaseEntity() {
    fun toDomain(): BookingSchedule =
        BookingSchedule(
            checkInAt = checkInAt,
            checkOutAt = checkOutAt,
        )
}
