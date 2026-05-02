package com.reservation.support.money

import java.math.BigDecimal

data class Money(
    val amount: BigDecimal,
) {
    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }

    constructor(amount: Long) : this(amount = BigDecimal.valueOf(amount))

    constructor(amount: Double) : this(amount = BigDecimal.valueOf(amount))

    constructor(amount: Int) : this(amount = BigDecimal.valueOf(amount.toLong()))

    operator fun plus(money: Money): Money = Money(amount.add(money.amount))

    operator fun minus(money: Money): Money = Money(amount.subtract(money.amount))

    fun multiply(money: Money): Money = Money(amount.multiply(money.amount))

    fun isGreaterThan(money: Money): Boolean = amount > money.amount

    fun isLessThan(money: Money): Boolean = amount < money.amount

    fun isEqualsThan(money: Money): Boolean = amount.compareTo(money.amount) == 0

    fun toLong(): Long = amount.toLong()

    fun toDouble(): Double = amount.toDouble()

    fun toInt(): Int = amount.toInt()

    fun abs(): Money = Money(amount.abs())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = amount.stripTrailingZeros().hashCode()
}
