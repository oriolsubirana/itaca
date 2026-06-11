package cat.subi.itaca.training.domain

import java.math.BigDecimal

/**
 * Weight of a set in kilograms. Zero is valid (bodyweight exercises).
 */
@JvmInline
value class Weight private constructor(
    val kg: BigDecimal,
) : Comparable<Weight> {
    fun increasedByStandardStep(): Weight = Weight(kg + STANDARD_STEP_KG)

    override fun compareTo(other: Weight): Int = kg.compareTo(other.kg)

    override fun toString(): String = "${kg.stripTrailingZeros().toPlainString()} kg"

    companion object {
        private val MAX_KG = BigDecimal(500)
        private val STANDARD_STEP_KG = BigDecimal("2.5")

        fun ofKg(kg: Double): Weight = ofKg(BigDecimal.valueOf(kg))

        fun ofKg(kg: BigDecimal): Weight {
            require(kg.signum() >= 0) { "Weight cannot be negative: $kg" }
            require(kg <= MAX_KG) { "Weight out of range: $kg kg" }
            return Weight(kg.stripTrailingZeros())
        }
    }
}
