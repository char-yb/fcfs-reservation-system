package com.reservation.domain.product

import java.time.LocalDateTime

data class BookingSchedule(
    val checkInAt: LocalDateTime,
    val checkOutAt: LocalDateTime,
)
