package cat.subi.itaca.profile.application

import cat.subi.itaca.profile.domain.ActivityLevel
import cat.subi.itaca.profile.domain.BodyMetrics
import cat.subi.itaca.profile.domain.CalorieCalculator
import cat.subi.itaca.profile.domain.Goal
import cat.subi.itaca.profile.domain.Sex
import cat.subi.itaca.profile.domain.WeightTrajectory
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

/** The profile plus its derived calorie targets (null while the data is incomplete). */
data class ProfileDto(
    val weightKg: Double?,
    val heightCm: Int?,
    val birthDate: String?,
    val sex: String?,
    val activityLevel: String?,
    val goal: String?,
    val age: Int?,
    val bmr: Int?,
    val tdee: Int?,
    val baseTarget: Int?,
    val targetWeightKg: Double?,
    val targetDate: String?,
    /** The trajectory from the current weight to the target; null without a target or a future date. */
    val weightPlan: WeightPlanDto?,
)

data class WeightPlanDto(
    val weeks: Double,
    val weeklyChangeKg: Double,
    val weeklyPercent: Double,
    /** reached | sustainable | demanding | aggressive */
    val pace: String,
    val reachableKg: Double,
)

/** Partial update from the profile form (any field may be cleared). */
data class ProfileCommand(
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val birthDate: String? = null,
    val sex: String? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
    val targetWeightKg: Double? = null,
    val targetDate: String? = null,
)

@Service
class ProfileService(
    private val jdbc: JdbcTemplate,
) : ChatTools {
    fun get(): ProfileDto = jdbc.query("SELECT * FROM profile WHERE id = 1", { rs, _ -> rs.toDto() }).single()

    @Tool(
        name = "query_profile",
        description =
            "Oriol's profile: weight, height, age, activity level and goal, the derived calorie targets (BMR, " +
                "TDEE, goal-adjusted base) and, if set, the target weight by a date with its trajectory " +
                "(weekly change, %/week, pace: reached / sustainable / demanding / aggressive, and what a " +
                "sustainable pace actually reaches). Use it for calorie targets, g/kg fueling maths and " +
                "race-weight questions. Read-only: changes are made in the Perfil screen.",
    )
    fun queryProfile(): ProfileDto = get()

    @Transactional
    fun save(command: ProfileCommand): ProfileDto {
        // Validate enums before writing (throws -> 400) and store their canonical wire form.
        val sex = command.sex?.let { Sex.from(it).wire }
        val activity = command.activityLevel?.let { ActivityLevel.from(it).wire }
        val goal = command.goal?.let { Goal.from(it).wire }
        val birthDate = command.birthDate?.let(LocalDate::parse)
        val targetDate = command.targetDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        require(command.targetWeightKg == null || command.targetWeightKg > 0) { "Target weight must be positive" }
        jdbc.update(
            """
            UPDATE profile
            SET weight_kg = ?, height_cm = ?, birth_date = ?, sex = ?, activity_level = ?, goal = ?,
                target_weight_kg = ?, target_date = ?, updated_at = now()
            WHERE id = 1
            """.trimIndent(),
            command.weightKg,
            command.heightCm,
            birthDate?.let(java.sql.Date::valueOf),
            sex,
            activity,
            goal,
            command.targetWeightKg,
            targetDate?.let(java.sql.Date::valueOf),
        )
        return get()
    }

    private fun weightPlan(
        weightKg: Double?,
        targetKg: Double?,
        targetDate: LocalDate?,
    ): WeightPlanDto? {
        if (weightKg == null || targetKg == null || targetDate == null) return null
        return WeightTrajectory.plan(weightKg, targetKg, LocalDate.now(), targetDate)?.let {
            WeightPlanDto(
                weeks = round1(it.weeks),
                weeklyChangeKg = round2(it.weeklyChangeKg),
                weeklyPercent = round2(it.weeklyPercent),
                pace = it.pace.name.lowercase(),
                reachableKg = round1(it.reachableKg),
            )
        }
    }

    private fun round1(v: Double): Double = Math.round(v * TENTHS) / TENTHS

    private fun round2(v: Double): Double = Math.round(v * HUNDREDTHS) / HUNDREDTHS

    private fun java.sql.ResultSet.toDto(): ProfileDto {
        val weightKg = (getObject("weight_kg") as? Number)?.toDouble()
        val heightCm = (getObject("height_cm") as? Number)?.toInt()
        val birthDate = getDate("birth_date")?.toLocalDate()
        val sex = getString("sex")
        val activity = getString("activity_level")
        val goal = getString("goal")
        val targetWeightKg = (getObject("target_weight_kg") as? Number)?.toDouble()
        val targetDate = getDate("target_date")?.toLocalDate()
        val age = birthDate?.let { Period.between(it, LocalDate.now()).years }
        val complete = listOf<Any?>(weightKg, heightCm, age, sex, activity, goal).all { it != null }
        val targets =
            if (complete) {
                CalorieCalculator.targets(
                    BodyMetrics(
                        Sex.from(sex!!),
                        weightKg!!,
                        heightCm!!.toDouble(),
                        age!!,
                        ActivityLevel.from(activity!!),
                        Goal.from(goal!!),
                    ),
                )
            } else {
                null
            }
        return ProfileDto(
            weightKg = weightKg,
            heightCm = heightCm,
            birthDate = birthDate?.toString(),
            sex = sex,
            activityLevel = activity,
            goal = goal,
            age = age,
            bmr = targets?.bmr,
            tdee = targets?.tdee,
            baseTarget = targets?.baseTarget,
            targetWeightKg = targetWeightKg,
            targetDate = targetDate?.toString(),
            weightPlan = weightPlan(weightKg, targetWeightKg, targetDate),
        )
    }

    private companion object {
        const val TENTHS = 10.0
        const val HUNDREDTHS = 100.0
    }
}
