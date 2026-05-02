package com.reservation

import com.reservation.application.product.ProductService
import com.reservation.fixture.RedisContainerFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class FcfsReservationApplicationTests {
    @MockitoBean
    lateinit var productService: ProductService

    @Autowired
    lateinit var environment: Environment

    @Test
    @DisplayName("애플리케이션 컨텍스트를 로딩한다")
    fun contextLoads() {
    }

    @Test
    @DisplayName("Redis CircuitBreaker 설정값을 yml에서 로딩한다")
    fun redisCircuitBreakerProperties() {
        environment.getProperty("resilience4j.circuitbreaker.instances.redis.sliding-window-size", Int::class.java) shouldBe 100
        environment.getProperty("resilience4j.circuitbreaker.instances.redis.minimum-number-of-calls", Int::class.java) shouldBe 20
        environment.getProperty("resilience4j.circuitbreaker.instances.redis.failure-rate-threshold", Float::class.java) shouldBe 50.0f
        environment.containsProperty("resilience4j.circuitbreaker.instances.redis.slow-call-duration-threshold") shouldBe false
        environment.containsProperty("resilience4j.circuitbreaker.instances.redis.slow-call-rate-threshold") shouldBe false
        environment.getProperty(
            "resilience4j.circuitbreaker.instances.redis.permitted-number-of-calls-in-half-open-state",
            Int::class.java,
        ) shouldBe 10
        environment.getProperty(
            "resilience4j.circuitbreaker.instances.redis.automatic-transition-from-open-to-half-open-enabled",
            Boolean::class.java,
        ) shouldBe true
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) = RedisContainerFixture.registerProperties(registry)
    }
}
