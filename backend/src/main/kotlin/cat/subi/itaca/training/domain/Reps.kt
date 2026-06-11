package cat.subi.itaca.training.domain

/**
 * Repeticiones de una serie. La progresión conservadora sube peso solo
 * cuando se supera el objetivo con margen (al menos 2 reps por encima).
 */
@JvmInline
value class Reps private constructor(val value: Int) : Comparable<Reps> {

    fun exceedsWithMargin(target: Reps): Boolean = value >= target.value + PROGRESSION_MARGIN

    override fun compareTo(other: Reps): Int = value.compareTo(other.value)

    override fun toString(): String = "$value reps"

    companion object {
        private const val PROGRESSION_MARGIN = 2
        private const val MAX_REPS = 200

        fun of(value: Int): Reps {
            require(value in 1..MAX_REPS) { "Repeticiones fuera de rango: $value" }
            return Reps(value)
        }
    }
}
