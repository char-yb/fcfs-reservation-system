package com.reservation.storage.rdb.product

import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductType
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class ProductEntity(
    @Column(name = "name", nullable = false, length = 200)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: ProductType,
) : BaseEntity() {
    fun toDomain(): Product =
        Product(
            id = id,
            name = name,
            type = type,
        )
}
