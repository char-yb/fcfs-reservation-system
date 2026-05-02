package com.reservation.api.booking

import com.reservation.application.booking.BookingFacade
import com.reservation.application.booking.command.BookingCommand
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.product.StockCounterRepository
import com.reservation.fixture.RedisContainerFixture
import com.reservation.storage.rdb.order.OrderJpaRepository
import com.reservation.storage.rdb.payment.PaymentJpaRepository
import com.reservation.storage.rdb.product.ProductEntity
import com.reservation.storage.rdb.product.ProductJpaRepository
import com.reservation.storage.rdb.product.ProductStockEntity
import com.reservation.storage.rdb.product.ProductStockJpaRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.redis.RedisUnavailableException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class BookingFacadeConcurrencyTest {
    @Autowired
    lateinit var bookingFacade: BookingFacade

    @Autowired
    lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    lateinit var productStockJpaRepository: ProductStockJpaRepository

    @Autowired
    lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    lateinit var paymentJpaRepository: PaymentJpaRepository

    @MockitoBean
    lateinit var stockCounterRepository: StockCounterRepository

    @BeforeEach
    fun setUp() {
        paymentJpaRepository.deleteAll()
        orderJpaRepository.deleteAll()
        productStockJpaRepository.deleteAll()
        productJpaRepository.deleteAll()
        Mockito.reset(stockCounterRepository)
    }

    @Test
    @DisplayName("Redis 장애 fallback에서 동일 주문 키 동시 요청은 하나만 확정한다")
    fun sameOrderKeyWithRedisFallback() {
        val productId = saveOpenProductWithStock(quantity = 2)
        Mockito
            .`when`(stockCounterRepository.decrement(productId))
            .thenThrow(RedisUnavailableException("redis down"))
        val orderKey = UUID.randomUUID().toString()
        val command = bookingCommand(productId = productId, orderKey = orderKey)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val futures =
            (1..2).map {
                executor.submit<Result<OrderStatus>> {
                    start.await()
                    runCatching { bookingFacade.booking(command).status }
                }
            }

        start.countDown()
        val results = futures.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        results.count { it.getOrNull() == OrderStatus.CONFIRMED } shouldBe 1
        results.count { result ->
            val exception = result.exceptionOrNull()
            exception is ErrorException && exception.errorType == ErrorType.DUPLICATE_REQUEST
        } shouldBe 1
        orderJpaRepository.findAll().count { it.status == OrderStatus.CONFIRMED } shouldBe 1
        productStockJpaRepository.findById(productId).orElseThrow().remainingQuantity shouldBe 1
        paymentJpaRepository.findAll().size shouldBe 1
    }

    private fun saveOpenProductWithStock(quantity: Int): Long {
        val now = LocalDateTime.now()
        val product =
            productJpaRepository.saveAndFlush(
                ProductEntity(
                    name = "room",
                    price = 100_000L,
                    checkInAt = now.plusDays(1),
                    checkOutAt = now.plusDays(2),
                    saleOpenAt = now.minusMinutes(1),
                ),
            )
        productStockJpaRepository.saveAndFlush(
            ProductStockEntity(
                productId = product.id,
                totalQuantity = quantity,
                remainingQuantity = quantity,
            ),
        )
        return product.id
    }

    private fun bookingCommand(
        productId: Long,
        orderKey: String,
    ): BookingCommand =
        BookingCommand(
            productId = productId,
            userId = 1L,
            totalAmount = 100_000L,
            orderKey = orderKey,
            payments =
                listOf(
                    PaymentCommand(
                        method = PaymentMethod.CREDIT_CARD,
                        amount = 100_000L,
                        userId = 1L,
                        attributes = mapOf("cardToken" to "card-token"),
                    ),
                ),
        )

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            RedisContainerFixture.registerProperties(registry)
        }
    }
}
