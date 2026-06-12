package cat.subi.itaca.training.domain

/**
 * Repetitions of a set. Conservative progression only increases weight
 * when the target is exceeded with margin (at least 2 reps above).
 */
@JvmInline
value class Reps private constructor(
    val value: Int,
) : Comparable<Reps> {
    fun exceedsWithMargin(target: Reps): Boolean = value >= target.value + PROGRESSION_MARGIN

    override fun compareTo(other: Reps): Int = value.compareTo(other.value)

    override fun toString(): String = "$value reps"

    companion object {
        private const val PROGRESSION_MARGIN = 2
        private const val MAX_REPS = 200

        fun of(value: Int): Reps {
            require(value in 1..MAX_REPS) { "Reps out of range: $value" }
            return Reps(value)
        }
    }
}
