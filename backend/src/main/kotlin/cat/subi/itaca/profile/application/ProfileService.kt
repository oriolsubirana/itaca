package cat.subi.itaca.profile.application

import cat.subi.itaca.profile.domain.ActivityLevel
import cat.subi.itaca.profile.domain.BodyMetrics
import cat.subi.itaca.profile.domain.CalorieCalculator
import cat.subi.itaca.profile.domain.Goal
import cat.subi.itaca.profile.domain.Sex
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
)

/** Partial update from the profile form (any field may be cleared). */
data class ProfileCommand(
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val birthDate: String? = null,
    val sex: String? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
)

@Service
class ProfileService(
    private val jdbc: JdbcTemplate,
) {
    fun get(): ProfileDto = jdbc.query("SELECT * FROM profile WHERE id = 1", { rs, _ -> rs.toDto() }).single()

    @Transactional
    fun save(command: ProfileCommand): ProfileDto {
        // Validate enums before writing (throws -> 400) and store their canonical wire form.
        val sex = command.sex?.let { Sex.from(it).wire }
        val activity = command.activityLevel?.let { ActivityLevel.from(it).wire }
        val goal = command.goal?.let { Goal.from(it).wire }
        val birthDate = command.birthDate?.let(LocalDate::parse)
        jdbc.update(
            """
            UPDATE profile
            SET weight_kg = ?, height_cm = ?, birth_date = ?, sex = ?, activity_level = ?, goal = ?, updated_at = now()
            WHERE id = 1
            """.trimIndent(),
            command.weightKg,
            command.heightCm,
            birthDate?.let(java.sql.Date::valueOf),
            sex,
            activity,
            goal,
        )
        return get()
    }

    private fun java.sql.ResultSet.toDto(): ProfileDto {
        val weightKg = (getObject("weight_kg") as? Number)?.toDouble()
        val heightCm = (getObject("height_cm") as? Number)?.toInt()
        val birthDate = getDate("birth_date")?.toLocalDate()
        val sex = getString("sex")
        val activity = getString("activity_level")
        val goal = getString("goal")
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
        )
    }
}
