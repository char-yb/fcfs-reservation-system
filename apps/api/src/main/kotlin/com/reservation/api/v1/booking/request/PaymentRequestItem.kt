package com.reservation.api.v1.booking.request

import com.reservation.domain.payment.PaymentMethod
import java.math.BigDecimal

data class PaymentRequestItem(
    val method: PaymentMethod,
    val amount: BigDecimal,
    val attributes: Map<String, String> = emptyMap(),
)
