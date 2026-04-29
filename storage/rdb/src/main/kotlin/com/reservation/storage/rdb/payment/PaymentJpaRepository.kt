package com.reservation.storage.rdb.payment

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findAllByOrderId(orderId: Long): List<PaymentEntity>
}
