package com.reservation.api.booking

import com.reservation.application.booking.BookingFacade
import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.payment.command.PaymentCommand
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.product.ProductType
import com.reservation.domain.product.StockCounterRepository
import com.reservation.fixture.RedisContainerFixture
import com.reservation.storage.rdb.order.OrderJpaRepository
import com.reservation.storage.rdb.order.product.OrderProductJpaRepository
import com.reservation.storage.rdb.payment.PaymentJpaRepository
import com.reservation.storage.rdb.product.ProductEntity
import com.reservation.storage.rdb.product.ProductJpaRepository
import com.reservation.storage.rdb.product.booking.BookingScheduleEntity
import com.reservation.storage.rdb.product.booking.BookingScheduleJpaRepository
import com.reservation.storage.rdb.product.option.ProductOptionEntity
import com.reservation.storage.rdb.product.option.ProductOptionJpaRepository
import com.reservation.storage.rdb.product.stock.ProductStockEntity
import com.reservation.storage.rdb.product.stock.ProductStockJpaRepository
import com.reservation.storage.rdb.user.point.UserPointEntity
import com.reservation.storage.rdb.user.point.UserPointJpaRepository
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
    lateinit var productOptionJpaRepository: ProductOptionJpaRepository

    @Autowired
    lateinit var bookingScheduleJpaRepository: BookingScheduleJpaRepository

    @Autowired
    lateinit var productStockJpaRepository: ProductStockJpaRepository

    @Autowired
    lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    lateinit var orderProductJpaRepository: OrderProductJpaRepository

    @Autowired
    lateinit var paymentJpaRepository: PaymentJpaRepository

    @Autowired
    lateinit var userPointJpaRepository: UserPointJpaRepository

    @MockitoBean
    lateinit var stockCounterRepository: StockCounterRepository

    @BeforeEach
    fun setUp() {
        paymentJpaRepository.deleteAll()
        orderProductJpaRepository.deleteAll()
        orderJpaRepository.deleteAll()
        productStockJpaRepository.deleteAll()
        bookingScheduleJpaRepository.deleteAll()
        productOptionJpaRepository.deleteAll()
        productJpaRepository.deleteAll()
        userPointJpaRepository.deleteAll()
        Mockito.reset(stockCounterRepository)
    }

    @Test
    @DisplayName("Redis 장애 fallback에서 동일 주문 키 동시 요청은 하나만 확정한다")
    fun sameOrderKeyWithRedisFallback() {
        val productOptionId = saveOpenBookingOptionWithStock(quantity = 2)
        Mockito
            .`when`(stockCounterRepository.decrement(productOptionId))
            .thenThrow(RedisUnavailableException("redis down"))
        val orderKey = UUID.randomUUID().toString()
        val command = bookingCommand(productOptionId = productOptionId, orderKey = orderKey)
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
        orderProductJpaRepository.findAll().count { it.productOptionId == productOptionId } shouldBe 1
        productStockJpaRepository.findById(productOptionId).orElseThrow().remainingQuantity shouldBe 1
        paymentJpaRepository.findAll().size shouldBe 1
    }

    @Test
    @DisplayName("같은 사용자 Y 포인트 동시 결제는 잔액 범위 안에서만 확정한다")
    fun sameUserYPointConcurrentDeduct() {
        val productOptionId = saveOpenBookingOptionWithStock(quantity = 2)
        userPointJpaRepository.save(UserPointEntity(userId = 1L, pointBalance = 100_000L))
        Mockito
            .`when`(stockCounterRepository.decrement(productOptionId))
            .thenThrow(RedisUnavailableException("redis down"))
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val futures =
            (1..2).map {
                executor.submit<Result<OrderStatus>> {
                    start.await()
                    runCatching {
                        bookingFacade
                            .booking(
                                bookingCommand(
                                    productOptionId = productOptionId,
                                    orderKey = UUID.randomUUID().toString(),
                                    payments =
                                        listOf(
                                            PaymentCommand(
                                                method = PaymentMethod.Y_POINT,
                                                amount = 100_000L,
                                                userId = 1L,
                                            ),
                                        ),
                                ),
                            ).status
                    }
                }
            }

        start.countDown()
        val results = futures.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        results.count { it.getOrNull() == OrderStatus.CONFIRMED } shouldBe 1
        results.count { result ->
            val exception = result.exceptionOrNull()
            exception is ErrorException && exception.errorType == ErrorType.INSUFFICIENT_POINT
        } shouldBe 1
        userPointJpaRepository.findById(1L).orElseThrow().pointBalance shouldBe 0L
        orderJpaRepository.findAll().count { it.status == OrderStatus.CONFIRMED } shouldBe 1
        orderJpaRepository.findAll().count { it.status == OrderStatus.FAILED } shouldBe 1
        productStockJpaRepository.findById(productOptionId).orElseThrow().remainingQuantity shouldBe 1
        paymentJpaRepository.findAll().size shouldBe 1
    }

    private fun saveOpenBookingOptionWithStock(quantity: Int): Long {
        val now = LocalDateTime.now()
        val product =
            productJpaRepository.save(
                ProductEntity(
                    name = "room",
                    type = ProductType.BOOKING,
                ),
            )
        val option =
            productOptionJpaRepository.save(
                ProductOptionEntity(
                    productId = product.id,
                    name = "standard",
                    price = 100_000L,
                    saleOpenAt = now.minusMinutes(1),
                ),
            )
        bookingScheduleJpaRepository.save(
            BookingScheduleEntity(
                productOptionId = option.id,
                checkInAt = now.plusDays(1),
                checkOutAt = now.plusDays(2),
            ),
        )
        productStockJpaRepository.save(
            ProductStockEntity(
                productOptionId = option.id,
                totalQuantity = quantity,
                remainingQuantity = quantity,
            ),
        )
        return option.id
    }

    private fun bookingCommand(
        productOptionId: Long,
        orderKey: String,
        payments: List<PaymentCommand> =
            listOf(
                PaymentCommand(
                    method = PaymentMethod.CREDIT_CARD,
                    amount = 100_000L,
                    userId = 1L,
                    attributes = mapOf("cardToken" to "card-token"),
                ),
            ),
    ): BookingCommand =
        BookingCommand(
            productOptionId = productOptionId,
            userId = 1L,
            totalAmount = 100_000L,
            orderKey = orderKey,
            payments = payments,
        )

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            RedisContainerFixture.registerProperties(registry)
        }
    }
}
