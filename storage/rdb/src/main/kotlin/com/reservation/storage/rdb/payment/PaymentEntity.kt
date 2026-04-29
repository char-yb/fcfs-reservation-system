package com.reservation.storage.rdb.payment

import com.reservation.domain.payment.Payment
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStatus
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "payments",
    indexes = [Index(name = "idx_order_id", columnList = "order_id")],
)
class PaymentEntity(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    val method: PaymentMethod,
    @Column(name = "amount", nullable = false)
    val amount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING,
    @Column(name = "pg_transaction_id", length = 100)
    var pgTransactionId: String? = null,
    @Column(name = "external_request_id", length = 100)
    val externalRequestId: String? = null,
) : BaseEntity() {
    fun toDomain(): Payment =
        Payment(
            id = id,
            orderId = orderId,
            method = method,
            amount = amount,
            status = status,
            pgTransactionId = pgTransactionId,
            externalRequestId = externalRequestId,
        )

    companion object {
        fun create(
            orderId: Long,
            method: PaymentMethod,
            amount: Long,
            externalRequestId: String? = null,
        ): PaymentEntity =
            PaymentEntity(
                orderId = orderId,
                method = method,
                amount = amount,
                externalRequestId = externalRequestId,
            )
    }
}
