package cat.subi.itaca.training.application

import cat.subi.itaca.training.domain.ProgressionPolicy
import cat.subi.itaca.training.domain.Reps
import cat.subi.itaca.training.domain.Weight
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class SessionSummaryDto(
    val id: Long,
    val date: String,
    val routine: String,
    val completed: Boolean,
    val summary: String,
)

data class SessionExerciseDto(
    val name: String,
    val sets: String,
)

data class SessionDetailDto(
    val id: Long,
    val date: String,
    val routine: String,
    val completed: Boolean,
    val exercises: List<SessionExerciseDto>,
)

data class ProgressionPointDto(
    val date: String,
    val weight: Double,
    val reps: Int,
)

data class ExerciseProgressionDto(
    val id: Long,
    val name: String,
    val target: String,
    val points: List<ProgressionPointDto>,
    val lastWeight: Double?,
    val lastReps: Int?,
    val suggestedWeight: Double?,
)

/** Read side for the Gym dashboard: session history and per-exercise progression. */
@Service
class TrainingHistoryQueries(
    private val jdbc: JdbcTemplate,
) {
    private val progression = ProgressionPolicy()

    fun sessions(limit: Int = 20): List<SessionSummaryDto> =
        jdbc
            .query(
                """
                SELECT w.id, w.date, r.name, w.completed
                FROM workouts w JOIN routines r ON r.id = w.routine_id
                ORDER BY w.date DESC, w.id DESC LIMIT ?
                """.trimIndent(),
                { rs, _ ->
                    SessionSummaryDto(
                        rs.getLong("id"),
                        rs.getDate("date").toLocalDate().toString(),
                        rs.getString("name"),
                        rs.getBoolean("completed"),
                        summaryOf(rs.getLong("id")),
                    )
                },
                limit,
            )

    fun sessionDetail(id: Long): SessionDetailDto {
        val meta =
            jdbc
                .query(
                    """
                    SELECT w.date, r.name, w.completed
                    FROM workouts w JOIN routines r ON r.id = w.routine_id WHERE w.id = ?
                    """.trimIndent(),
                    { rs, _ ->
                        Triple(
                            rs.getDate("date").toLocalDate().toString(),
                            rs.getString("name"),
                            rs.getBoolean("completed"),
                        )
                    },
                    id,
                ).firstOrNull() ?: throw NoSuchElementException("Workout $id not found")
        val sets = setRows(id)
        val byExercise = LinkedHashMap<String, MutableList<SetRow>>()
        sets.forEach { byExercise.getOrPut(it.name) { mutableListOf() }.add(it) }
        val exercises =
            byExercise.map { (name, rows) ->
                SessionExerciseDto(name, rows.joinToString(" · ") { "${fmtW(it.weight)}×${it.reps}" })
            }
        return SessionDetailDto(id, meta.first, meta.second, meta.third, exercises)
    }

    fun exercises(): List<ExerciseRef> =
        jdbc.query("SELECT id, name FROM exercises ORDER BY name") { rs, _ ->
            ExerciseRef(rs.getLong("id"), rs.getString("name"))
        }

    fun exerciseProgression(exerciseId: Long): ExerciseProgressionDto {
        val name =
            jdbc.queryForObject("SELECT name FROM exercises WHERE id = ?", String::class.java, exerciseId)
                ?: throw NoSuchElementException("Exercise $exerciseId not found")
        val points =
            jdbc.query(
                """
                SELECT w.date, top.weight_kg, top.reps FROM (
                    SELECT s.workout_id, s.weight_kg, s.reps,
                           ROW_NUMBER() OVER (PARTITION BY s.workout_id ORDER BY s.weight_kg DESC, s.reps DESC) rn
                    FROM sets s WHERE s.exercise_id = ?
                ) top JOIN workouts w ON w.id = top.workout_id
                WHERE top.rn = 1 AND w.completed
                ORDER BY w.date, top.workout_id
                """.trimIndent(),
                { rs, _ ->
                    ProgressionPointDto(
                        rs.getDate("date").toLocalDate().toString(),
                        rs.getBigDecimal("weight_kg").toDouble(),
                        rs.getInt("reps"),
                    )
                },
                exerciseId,
            )
        val last = points.lastOrNull()
        val suggested =
            last?.let {
                progression.suggestNextWeight(Weight.ofKg(it.weight), Reps.of(it.reps)).kg.toDouble()
            }
        return ExerciseProgressionDto(exerciseId, name, "3×6-8", points, last?.weight, last?.reps, suggested)
    }

    private data class SetRow(
        val name: String,
        val weight: Double,
        val reps: Int,
    )

    private fun setRows(workoutId: Long): List<SetRow> =
        jdbc.query(
            """
            SELECT e.name, s.weight_kg, s.reps FROM sets s JOIN exercises e ON e.id = s.exercise_id
            WHERE s.workout_id = ? ORDER BY s.position
            """.trimIndent(),
            { rs, _ -> SetRow(rs.getString("name"), rs.getBigDecimal("weight_kg").toDouble(), rs.getInt("reps")) },
            workoutId,
        )

    /** Top set per exercise (max weight, then reps), first three, e.g. "Press inclinado 50×9 · ...". */
    private fun summaryOf(workoutId: Long): String {
        val rows = setRows(workoutId)
        if (rows.isEmpty()) return "Sin series"
        val top = LinkedHashMap<String, SetRow>()
        rows.forEach { r ->
            val cur = top[r.name]
            if (cur == null || isHeavier(r, cur)) top[r.name] = r
        }
        val parts = top.values.take(SUMMARY_EXERCISES).map { "${it.name} ${fmtW(it.weight)}×${it.reps}" }
        return parts.joinToString(" · ") + if (top.size > SUMMARY_EXERCISES) " · …" else ""
    }

    private fun isHeavier(
        a: SetRow,
        b: SetRow,
    ): Boolean = a.weight > b.weight || (a.weight == b.weight && a.reps > b.reps)

    private fun fmtW(w: Double): String = (if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()).replace(".", ",")

    private companion object {
        const val SUMMARY_EXERCISES = 3
    }
}
