package com.reservation.application.payment

import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStrategy
import org.springframework.stereotype.Component

/**
 * 결제 수단(PaymentMethod)별 PaymentStrategy 구현체를 관리하는 레지스트리.
 *
 * Spring이 주입하는 List<PaymentStrategy>를 PaymentMethod 키로 변환해 보관한다.
 * 부트 시 PaymentMethod enum 전체 항목에 대한 구현체 존재 여부를 검증하므로,
 * 새 결제 수단 추가 시 Strategy 구현체가 누락되면 애플리케이션 기동이 실패한다.
 *
 * 새 결제 수단 추가 절차:
 * 1. PaymentMethod enum에 항목 추가
 * 2. PaymentStrategy 구현체 작성 후 @Component 등록
 * 3. (PG 연동이 필요한 경우) PgGatewayRegistry에 PaymentGateway 구현체도 등록
 */
@Component
class PaymentStrategyRegistry(
    strategies: List<PaymentStrategy>,
) {
    private val byMethod: Map<PaymentMethod, PaymentStrategy> =
        strategies.associateBy { it.method }

    init {
        PaymentMethod.entries.forEach { method ->
            requireNotNull(byMethod[method]) {
                "PaymentStrategy implementation missing for $method"
            }
        }
    }

    fun get(method: PaymentMethod): PaymentStrategy = checkNotNull(byMethod[method]) { "지원하지 않는 결제 수단: $method" }
}
