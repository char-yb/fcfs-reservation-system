package com.reservation.application.payment

import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PaymentMethod
import org.springframework.stereotype.Component

/**
 * 결제 수단(PaymentMethod)별 PaymentGateway 구현체를 관리하는 레지스트리.
 *
 * PaymentStrategyRegistry가 "어떤 전략으로 결제할 것인가"를 결정한다면,
 * 이 레지스트리는 "어떤 PG HTTP 클라이언트를 사용할 것인가"를 결정한다.
 *
 * Y_POINT처럼 외부 PG 호출이 없는 결제 수단은 이 레지스트리에 등록하지 않는다.
 * 실제 PG 구현체는 external:pg 모듈에 위치하며, 새 PG 추가 시 해당 모듈에만 구현체를 추가하면 된다.
 */
@Component
class PgGatewayRegistry(
    gateways: List<PaymentGateway>,
) {
    private val byMethod: Map<PaymentMethod, PaymentGateway> = gateways.associateBy { it.method }

    fun get(method: PaymentMethod): PaymentGateway = checkNotNull(byMethod[method]) { "PaymentGateway 미등록: $method" }
}
