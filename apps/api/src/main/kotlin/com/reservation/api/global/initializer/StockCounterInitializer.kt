package com.reservation.api.global.initializer

import com.reservation.application.product.StockService
import com.reservation.support.extension.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 기동 시 모든 상품 옵션의 Redis 재고 카운터(stock:{productOptionId})를 DB 기준으로 초기화한다.
 *
 * ## 필요한 이유
 * Booking API는 3-layer 재고 보호를 사용한다.
 * - Layer 1: Redis atomic DECR — 빠른 매진 거절 (Fast-Fail)
 * - Layer 2: Redisson fair lock — DB 예약 구간 직렬화
 * - Layer 3: DB 조건부 UPDATE — 최후 정합성 보장
 *
 * Redis에 `stock:{productOptionId}` 키가 없는 상태에서 DECR을 호출하면 Redis는 값을 0으로 간주해 -1을 반환한다.
 * 이 경우 Layer 1이 모든 요청을 STOCK_SOLD_OUT으로 즉시 거절하므로 콜드 스타트 전에 반드시 초기화가 필요하다.
 *
 * ## 한계 및 운영 주의사항
 * 애플리케이션이 실행 중인 상태에서 Redis만 재기동되면 이 초기화가 재실행되지 않는다.
 * 해당 상황에서는 Circuit Breaker가 OPEN 되어 DB 폴백(Layer 3)으로 정합성을 유지하지만,
 * Redis 복구 후 카운터를 다시 세팅하려면 admin 엔드포인트 또는 재배포가 필요하다.
 *
 * 또한 롤링 배포(다중 인스턴스) 환경에서는 인스턴스마다 initializeAll()이 실행되어
 * 판매 중인 상품의 카운터를 DB 기준으로 덮어쓰는 오버셀링 위험이 있다.
 * 실제 운영 환경에서는 판매 이벤트 오픈 시 해당 상품만 1회 초기화하는 admin API로 대체해야 한다.
 * 혹은 배치 잡으로 초기화 작업을 분리해도 된다.
 */
@Component
class StockCounterInitializer(
    private val stockService: StockService,
) {
    private val log by logger()

    @EventListener(ApplicationReadyEvent::class)
    fun initializeAll() {
        val stocks = stockService.getStocks()
        if (stocks.isEmpty()) {
            log.info { "초기화 대상 상품 옵션 없음" }
            return
        }

        var success = 0
        var failed = 0
        stocks.forEach { stock ->
            runCatching {
                stockService.initializeStockCounter(stock.productOptionId)
            }.onSuccess {
                success += 1
            }.onFailure { ex ->
                failed += 1
                log.error(ex) { "재고 카운터 초기화 실패 productOptionId=${stock.productOptionId}" }
            }
        }
        log.info { "재고 카운터 초기화 완료 total=${stocks.size} success=$success failed=$failed" }
    }
}
