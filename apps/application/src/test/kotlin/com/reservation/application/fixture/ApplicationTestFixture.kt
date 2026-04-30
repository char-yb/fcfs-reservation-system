package com.reservation.application.fixture

import com.reservation.application.booking.BookingFacade
import com.reservation.application.order.OrderService
import com.reservation.application.payment.PaymentService
import com.reservation.application.payment.PaymentStrategyRegistry
import com.reservation.application.payment.PaymentValidator
import com.reservation.application.payment.fixture.RecordingPaymentStrategy
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockLockService
import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.pg.PgCancelResponse
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.domain.payment.pg.PgChargeResponse
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository
import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository
import com.reservation.domain.product.StockCounterRepository
import com.reservation.domain.user.UserPoint
import com.reservation.domain.user.UserPointRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.lock.DistributedLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

internal fun openProduct(
    id: Long = 1L,
    price: Long = 100_000L,
): Product {
    val now = LocalDateTime.of(2026, 4, 30, 10, 0)
    return Product(
        id = id,
        name = "room-$id",
        price = price,
        checkInAt = now.plusDays(1),
        checkOutAt = now.plusDays(2),
        saleOpenAt = now.minusMinutes(1),
    )
}

internal fun bookingFacade(
    orderRepository: FakeOrderRepository,
    stockRepository: FakeProductStockRepository,
    counterRepository: FakeStockCounterRepository = FakeStockCounterRepository(initialRemaining = 2L),
    failingMethod: PaymentMethod? = null,
): BookingFacade {
    val productService =
        ProductService(
            productRepository = FakeProductRepository(listOf(openProduct(id = 1L))),
            productStockRepository = stockRepository,
            stockCounterRepository = counterRepository,
        )
    val strategies =
        PaymentMethod.entries.map { method ->
            RecordingPaymentStrategy(
                method = method,
                events = mutableListOf(),
                payFailure =
                    if (method == failingMethod) {
                        ErrorException(ErrorType.PAYMENT_DECLINED)
                    } else {
                        null
                    },
            )
        }
    return BookingFacade(
        productService = productService,
        orderService = OrderService(orderRepository),
        paymentService =
            PaymentService(
                strategyRegistry = PaymentStrategyRegistry(strategies),
                validator = PaymentValidator(),
            ),
        stockLockService =
            StockLockService(
                stockCounterRepository = counterRepository,
                distributedLock = RecordingDistributedLock(),
            ),
    )
}

internal class FakeOrderRepository(
    initialOrders: List<Order> = emptyList(),
) : OrderRepository {
    val orders: MutableMap<Long, Order> = initialOrders.associateBy { it.id }.toMutableMap()
    private var nextId = (orders.keys.maxOrNull() ?: 0L) + 1L

    override fun findById(id: Long): Order? = orders[id]

    override fun findByOrderKey(orderKey: String): Order? = orders.values.firstOrNull { it.orderKey == orderKey }

    override fun save(order: Order): Order {
        val saved =
            if (order.id == 0L) {
                order.copy(id = nextId++)
            } else {
                order
            }
        orders[saved.id] = saved
        return saved
    }

    override fun updateStatus(
        id: Long,
        status: OrderStatus,
    ): Order {
        val updated = requireNotNull(orders[id]) { "order not found: $id" }.copy(status = status)
        orders[id] = updated
        return updated
    }
}

internal class FakeProductRepository(
    initialProducts: List<Product> = emptyList(),
) : ProductRepository {
    val products: MutableMap<Long, Product> = initialProducts.associateBy { it.id }.toMutableMap()

    override fun findById(id: Long): Product? = products[id]

    override fun findAll(): List<Product> = products.values.toList()
}

internal class FakeProductStockRepository(
    initialStocks: List<ProductStock> = emptyList(),
) : ProductStockRepository {
    val stocks: MutableMap<Long, ProductStock> = initialStocks.associateBy { it.productId }.toMutableMap()
    var incrementCalls = 0

    override fun findByProductId(productId: Long): ProductStock? = stocks[productId]

    override fun decrementStock(productId: Long): Boolean {
        val stock = stocks[productId] ?: return false
        if (stock.remainingQuantity <= 0) return false
        stocks[productId] = stock.copy(remainingQuantity = stock.remainingQuantity - 1)
        return true
    }

    override fun incrementStock(productId: Long) {
        incrementCalls += 1
        val stock = requireNotNull(stocks[productId]) { "stock not found: $productId" }
        stocks[productId] = stock.copy(remainingQuantity = stock.remainingQuantity + 1)
    }
}

internal class FakeStockCounterRepository(
    initialRemaining: Long = 1L,
) : StockCounterRepository {
    var remaining = initialRemaining
    var incrementCalls = 0
    val initialized = mutableMapOf<Long, Int>()

    override fun decrement(productId: Long): Long {
        remaining -= 1
        return remaining
    }

    override fun increment(productId: Long) {
        incrementCalls += 1
        remaining += 1
    }

    override fun initialize(
        productId: Long,
        quantity: Int,
    ) {
        initialized[productId] = quantity
        remaining = quantity.toLong()
    }
}

internal class FakeUserPointRepository(
    initialPoints: List<UserPoint> = emptyList(),
) : UserPointRepository {
    val points: MutableMap<Long, UserPoint> = initialPoints.associateBy { it.userId }.toMutableMap()

    override fun findByUserId(userId: Long): UserPoint? = points[userId]

    override fun save(userPoint: UserPoint): UserPoint {
        points[userPoint.userId] = userPoint
        return userPoint
    }
}

internal data class LockCall(
    val key: String,
    val waitTime: Duration,
    val leaseTime: Duration,
)

internal class RecordingDistributedLock(
    private val failure: RuntimeException? = null,
) : DistributedLock {
    val calls = mutableListOf<LockCall>()

    override fun <T> executeWithLock(
        key: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T,
    ): T {
        calls.add(LockCall(key = key, waitTime = waitTime, leaseTime = leaseTime))
        failure?.let { throw it }
        return action()
    }
}

internal class RecordingPaymentGateway(
    override val method: PaymentMethod,
    private val chargeFailure: ErrorException? = null,
) : PaymentGateway {
    val chargeRequests = mutableListOf<PgChargeRequest>()
    val cancelledTransactionIds = mutableListOf<String>()

    override fun charge(request: PgChargeRequest): PgChargeResponse {
        chargeRequests.add(request)
        chargeFailure?.let { throw it }
        return PgChargeResponse(transactionId = "pg_${method.name}", approvedAt = Instant.now())
    }

    override fun cancel(transactionId: String): PgCancelResponse {
        cancelledTransactionIds.add(transactionId)
        return PgCancelResponse(cancelledAt = Instant.now())
    }
}
