package cat.subi.itaca.health.domain

/**
 * 0-10 subjective scale used for pain, urgency and stress in the diary.
 */
@JvmInline
value class Score private constructor(
    val value: Int,
) {
    companion object {
        fun of(value: Int): Score {
            require(value in 0..10) { "Score goes from 0 to 10: $value" }
            return Score(value)
        }
    }
}
