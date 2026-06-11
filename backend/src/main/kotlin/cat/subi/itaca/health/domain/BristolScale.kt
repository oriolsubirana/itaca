package cat.subi.itaca.health.domain

/**
 * Bristol stool scale value (1-7) of a diary entry.
 */
@JvmInline
value class BristolScale private constructor(
    val value: Int,
) {
    companion object {
        fun of(value: Int): BristolScale {
            require(value in 1..7) { "Bristol scale goes from 1 to 7: $value" }
            return BristolScale(value)
        }
    }
}
