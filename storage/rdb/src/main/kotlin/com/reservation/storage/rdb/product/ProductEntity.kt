package com.reservation.storage.rdb.product

import com.reservation.domain.product.Product
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "products")
class ProductEntity(
    @Column(name = "name", nullable = false, length = 200)
    val name: String,

    @Column(name = "price", nullable = false)
    val price: Long,

    @Column(name = "check_in_at", nullable = false)
    val checkInAt: LocalDateTime,

    @Column(name = "check_out_at", nullable = false)
    val checkOutAt: LocalDateTime,

    @Column(name = "sale_open_at", nullable = false)
    val saleOpenAt: LocalDateTime,
) : BaseEntity() {

    fun toDomain(): Product = Product(
        id = id,
        name = name,
        price = price,
        checkInAt = checkInAt,
        checkOutAt = checkOutAt,
        saleOpenAt = saleOpenAt,
    )
}
