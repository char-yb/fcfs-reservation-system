package com.reservation.storage.rdb.product.option

import com.reservation.domain.product.ProductOption
import com.reservation.storage.rdb.common.BaseEntity
import com.reservation.support.money.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "product_options",
    indexes = [Index(name = "idx_product_options_product_id", columnList = "product_id")],
)
class ProductOptionEntity(
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    @Column(name = "name", nullable = false, length = 200)
    val name: String,
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    val price: BigDecimal,
    @Column(name = "sale_open_at", nullable = false)
    val saleOpenAt: LocalDateTime,
) : BaseEntity() {
    constructor(
        productId: Long,
        name: String,
        price: Long,
        saleOpenAt: LocalDateTime,
    ) : this(
        productId = productId,
        name = name,
        price = BigDecimal.valueOf(price),
        saleOpenAt = saleOpenAt,
    )

    fun toDomain(): ProductOption =
        ProductOption(
            id = id,
            productId = productId,
            name = name,
            price = Money(price),
            saleOpenAt = saleOpenAt,
        )
}
