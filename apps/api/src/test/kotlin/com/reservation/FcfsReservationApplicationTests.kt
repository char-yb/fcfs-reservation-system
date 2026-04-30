package com.reservation

import com.reservation.application.product.ProductService
import com.reservation.fixture.RedisContainerFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class FcfsReservationApplicationTests {
    @MockitoBean
    lateinit var productService: ProductService

    @Test
    @DisplayName("애플리케이션 컨텍스트를 로딩한다")
    fun contextLoads() {
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) = RedisContainerFixture.registerProperties(registry)
    }
}
