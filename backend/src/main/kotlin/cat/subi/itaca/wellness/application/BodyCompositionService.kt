package cat.subi.itaca.wellness.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** One scale measurement (any composition field may be missing — the scale doesn't always report them). */
data class BodyCompositionDto(
    val date: String,
    val weightKg: Double,
    val bmi: Double?,
    val bodyFatPct: Double?,
    val muscleKg: Double?,
    val waterPct: Double?,
    val boneKg: Double?,
    val visceralFat: Double?,
    val bmrKcal: Int?,
)

/** Recent measurements plus the trend over the window. */
data class BodyCompositionSummary(
    val measurements: List<BodyCompositionDto>,
    val latest: BodyCompositionDto?,
    val weightChangeKg: Double?,
)

/** A measurement to upsert (idempotent on the date; the Zepp sync re-sends freely). */
data class BodyCompositionCommand(
    val date: LocalDate,
    val weightKg: Double,
    val bmi: Double? = null,
    val bodyFatPct: Double? = null,
    val muscleKg: Double? = null,
    val waterPct: Double? = null,
    val boneKg: Double? = null,
    val visceralFat: Double? = null,
    val bmrKcal: Int? = null,
)

/**
 * Body composition from the Xiaomi scale, pushed by the external Zepp sync
 * (POST /api/wellness/body) and exposed read-only to the chat. Records and describes data only —
 * the health rule holds: no medical interpretation.
 */
@Service
class BodyCompositionService(
    private val jdbc: JdbcTemplate,
) : ChatTools {
    @Transactional
    fun upsert(c: BodyCompositionCommand): BodyCompositionDto =
        jdbc
            .query(
                """
                INSERT INTO body_composition (
                    measured_on, weight_kg, bmi, body_fat_pct, muscle_kg, water_pct, bone_kg,
                    visceral_fat, bmr_kcal
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (measured_on) DO UPDATE SET
                    weight_kg = EXCLUDED.weight_kg,
                    bmi = COALESCE(EXCLUDED.bmi, body_composition.bmi),
                    body_fat_pct = COALESCE(EXCLUDED.body_fat_pct, body_composition.body_fat_pct),
                    muscle_kg = COALESCE(EXCLUDED.muscle_kg, body_composition.muscle_kg),
                    water_pct = COALESCE(EXCLUDED.water_pct, body_composition.water_pct),
                    bone_kg = COALESCE(EXCLUDED.bone_kg, body_composition.bone_kg),
                    visceral_fat = COALESCE(EXCLUDED.visceral_fat, body_composition.visceral_fat),
                    bmr_kcal = COALESCE(EXCLUDED.bmr_kcal, body_composition.bmr_kcal),
                    updated_at = now()
                RETURNING *
                """.trimIndent(),
                MAPPER,
                java.sql.Date.valueOf(c.date),
                c.weightKg,
                c.bmi,
                c.bodyFatPct,
                c.muscleKg,
                c.waterPct,
                c.boneKg,
                c.visceralFat,
                c.bmrKcal,
            ).single()

    fun recent(days: Int): List<BodyCompositionDto> =
        jdbc.query(
            "SELECT * FROM body_composition WHERE measured_on >= ? ORDER BY measured_on DESC",
            MAPPER,
            java.sql.Date.valueOf(LocalDate.now().minusDays(days.toLong())),
        )

    @Tool(
        name = "query_body_composition",
        description =
            "Xiaomi scale measurements for the last days (default 30): weight, BMI, body fat %, muscle, " +
                "water %, bone, visceral fat and BMR, newest first, plus the weight change over the window. " +
                "Use it for any question about weight or body composition. Describe trends factually; " +
                "the profile's weight (used for the calorie target) is separate — suggest updating it in " +
                "Perfil when they differ.",
    )
    fun queryBodyComposition(
        @ToolParam(description = "How many days back to look (default 30)", required = false) days: Int?,
    ): BodyCompositionSummary {
        val rows = recent(days ?: DEFAULT_DAYS)
        return BodyCompositionSummary(
            measurements = rows,
            latest = rows.firstOrNull(),
            weightChangeKg =
                if (rows.size >= 2) {
                    Math.round((rows.first().weightKg - rows.last().weightKg) * TENTH) / TENTH
                } else {
                    null
                },
        )
    }

    private companion object {
        const val DEFAULT_DAYS = 30
        const val TENTH = 10.0
        val MAPPER =
            RowMapper { rs, _ ->
                fun dbl(c: String) = (rs.getObject(c) as? Number)?.toDouble()
                BodyCompositionDto(
                    date = rs.getDate("measured_on").toLocalDate().toString(),
                    weightKg = rs.getDouble("weight_kg"),
                    bmi = dbl("bmi"),
                    bodyFatPct = dbl("body_fat_pct"),
                    muscleKg = dbl("muscle_kg"),
                    waterPct = dbl("water_pct"),
                    boneKg = dbl("bone_kg"),
                    visceralFat = dbl("visceral_fat"),
                    bmrKcal = (rs.getObject("bmr_kcal") as? Number)?.toInt(),
                )
            }
    }
}
