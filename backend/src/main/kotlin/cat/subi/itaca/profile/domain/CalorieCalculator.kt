// The numbers here are the Mifflin-St Jeor equation, activity factors and goal deltas — the
// domain formula itself, not tunable magic constants.
@file:Suppress("MagicNumber")

package cat.subi.itaca.profile.domain

import kotlin.math.roundToInt

enum class Sex {
    MALE,
    FEMALE,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String): Sex =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
                ?: throw IllegalArgumentException("Unknown sex: $value")
    }
}

enum class ActivityLevel(
    val factor: Double,
) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String): ActivityLevel =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
                ?: throw IllegalArgumentException("Unknown activity level: $value")
    }
}

enum class Goal(
    val kcalAdjustment: Int,
) {
    LOSE(-500),
    MAINTAIN(0),
    GAIN(300),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String): Goal =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
                ?: throw IllegalArgumentException("Unknown goal: $value")
    }
}

/** The inputs to the calorie calculation. */
data class BodyMetrics(
    val sex: Sex,
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val activity: ActivityLevel,
    val goal: Goal,
)

/** BMR, daily expenditure (TDEE) and the goal-adjusted base target, all in kcal. */
data class CalorieTargets(
    val bmr: Int,
    val tdee: Int,
    val baseTarget: Int,
)

/** Mifflin-St Jeor. Pure: the training/flare adjustments are composed at the edge, not here. */
object CalorieCalculator {
    fun targets(m: BodyMetrics): CalorieTargets {
        val bmr =
            when (m.sex) {
                Sex.MALE -> 10 * m.weightKg + 6.25 * m.heightCm - 5 * m.age + 5
                Sex.FEMALE -> 10 * m.weightKg + 6.25 * m.heightCm - 5 * m.age - 161
            }
        val tdee = bmr * m.activity.factor
        val baseTarget = tdee + m.goal.kcalAdjustment
        return CalorieTargets(bmr.roundToInt(), tdee.roundToInt(), baseTarget.roundToInt())
    }
}
