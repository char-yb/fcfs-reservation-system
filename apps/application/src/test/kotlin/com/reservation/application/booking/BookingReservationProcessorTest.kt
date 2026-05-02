package com.reservation.application.booking

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.fixture.FakeOrderProductRepository
import com.reservation.application.fixture.FakeOrderRepository
import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.bookingCommand
import com.reservation.application.fixture.openBookingOption
import com.reservation.application.fixture.orderProduct
import com.reservation.application.fixture.pendingOrder
import com.reservation.application.fixture.productStock
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.product.BookingProductOption
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class BookingReservationProcessorTest :
    StringSpec({
        "분산락 내부 상태 변경 메서드는 독립 트랜잭션으로 실행된다" {
            val longType = requireNotNull(Long::class.javaPrimitiveType)
            val processorClass = BookingReservationProcessor::class.java

            processorClass
                .getDeclaredMethod("reserve", BookingCommand::class.java, BookingProductOption::class.java)
                .getAnnotation(Transactional::class.java)
                .propagation shouldBe Propagation.REQUIRES_NEW
            processorClass
                .getDeclaredMethod("confirm", longType)
                .getAnnotation(Transactional::class.java)
                .propagation shouldBe Propagation.REQUIRES_NEW
            processorClass
                .getDeclaredMethod("failAndRelease", longType, longType)
                .getAnnotation(Transactional::class.java)
                .propagation shouldBe Propagation.REQUIRES_NEW
        }

        "재고가 있으면 DB 재고를 차감하고 대기 주문을 생성한다" {
            val orderRepository = FakeOrderRepository()
            val orderProductRepository = FakeOrderProductRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 1)),
                )
            val processor = BookingReservationProcessor(stockRepository, orderRepository, orderProductRepository)

            val order = processor.reserve(bookingCommand(orderKey = "order-key"), openBookingOption())

            order.status shouldBe OrderStatus.PENDING
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 0
            orderRepository.orders[order.id] shouldBe order
            val orderProduct = orderProductRepository.orderProducts.values.single()
            orderProduct.orderId shouldBe order.id
            orderProduct.productId shouldBe 1L
            orderProduct.productOptionId shouldBe 1L
            orderProduct.confirmedAt shouldBe null
            orderProduct.canceledAt shouldBe null
        }

        "재고가 없으면 주문을 만들지 않고 매진으로 거부한다" {
            val orderRepository = FakeOrderRepository()
            val orderProductRepository = FakeOrderProductRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 0)),
                )
            val processor = BookingReservationProcessor(stockRepository, orderRepository, orderProductRepository)

            val exception =
                shouldThrow<ErrorException> {
                    processor.reserve(bookingCommand(orderKey = "order-key"), openBookingOption())
                }

            exception.errorType shouldBe ErrorType.STOCK_SOLD_OUT
            orderRepository.orders shouldBe emptyMap()
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 0
        }

        "같은 주문 키가 있으면 중복 요청으로 거부하고 재고를 차감하지 않는다" {
            val orderRepository =
                FakeOrderRepository(
                    listOf(pendingOrder()),
                )
            val orderProductRepository = FakeOrderProductRepository()
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 1)),
                )
            val processor = BookingReservationProcessor(stockRepository, orderRepository, orderProductRepository)

            val exception =
                shouldThrow<ErrorException> {
                    processor.reserve(bookingCommand(orderKey = "order-key"), openBookingOption())
                }

            exception.errorType shouldBe ErrorType.DUPLICATE_REQUEST
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
        }

        "실패 처리와 DB 재고 복구를 함께 수행한다" {
            val orderRepository =
                FakeOrderRepository(
                    listOf(pendingOrder()),
                )
            val orderProductRepository =
                FakeOrderProductRepository(
                    listOf(orderProduct(orderId = 1L)),
                )
            val stockRepository =
                FakeProductStockRepository(
                    listOf(productStock(remainingQuantity = 0)),
                )
            val processor = BookingReservationProcessor(stockRepository, orderRepository, orderProductRepository)

            val failed = processor.failAndRelease(orderId = 1L, productOptionId = 1L)

            failed.status shouldBe OrderStatus.FAILED
            orderProductRepository.findByOrderId(1L).single().canceledAt shouldNotBe null
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
        }

        "예약 주문을 확정 처리한다" {
            val orderRepository =
                FakeOrderRepository(
                    listOf(pendingOrder()),
                )
            val orderProductRepository =
                FakeOrderProductRepository(
                    listOf(orderProduct(orderId = 1L)),
                )
            val stockRepository = FakeProductStockRepository()
            val processor = BookingReservationProcessor(stockRepository, orderRepository, orderProductRepository)

            val confirmed = processor.confirm(orderId = 1L)

            confirmed.status shouldBe OrderStatus.CONFIRMED
            orderProductRepository.findByOrderId(1L).single().confirmedAt shouldNotBe null
        }
    })
