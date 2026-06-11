package cat.subi.itaca.training.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

data class ExerciseRef(
    val id: Long,
    val name: String,
)

data class RoutineExerciseRow(
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
)

data class LastSetRow(
    val weightKg: BigDecimal,
    val reps: Int,
    val date: LocalDate,
)

data class SetLine(
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int,
    val position: Int,
)

data class WorkoutRow(
    val id: Long,
    val date: LocalDate,
    val routineName: String,
    val completed: Boolean,
)

/**
 * Read side of the training context (light CQRS): plain SQL projections.
 */
@Component
class TrainingQueries(
    private val jdbc: JdbcTemplate,
) {
    fun routineByName(name: String): Pair<Long, String>? =
        jdbc
            .query(
                "SELECT id, name FROM routines WHERE lower(name) = lower(?)",
                { rs, _ -> rs.getLong("id") to rs.getString("name") },
                name.trim(),
            ).firstOrNull()

    fun routineNameOf(routineId: Long): String =
        jdbc.queryForObject("SELECT name FROM routines WHERE id = ?", String::class.java, routineId)!!

    /** Rotation Push -> Pull -> Leg -> Push, based on the last completed workout. */
    fun nextRoutineInRotation(): Pair<Long, String> {
        val lastRoutine =
            jdbc
                .query(
                    """
                    SELECT r.name FROM workouts w JOIN routines r ON r.id = w.routine_id
                    WHERE w.completed ORDER BY w.date DESC, w.id DESC LIMIT 1
                    """.trimIndent(),
                    { rs, _ -> rs.getString("name") },
                ).firstOrNull()
        val rotation = listOf("Push", "Pull", "Leg")
        val next =
            lastRoutine
                ?.let { rotation[(rotation.indexOf(it) + 1) % rotation.size] }
                ?: rotation.first()
        return routineByName(next)!!
    }

    fun exercisesOfRoutine(routineId: Long): List<RoutineExerciseRow> =
        jdbc.query(
            """
            SELECT re.exercise_id, e.name, re.position
            FROM routine_exercises re JOIN exercises e ON e.id = re.exercise_id
            WHERE re.routine_id = ? ORDER BY re.position
            """.trimIndent(),
            { rs, _ -> RoutineExerciseRow(rs.getLong("exercise_id"), rs.getString("name"), rs.getInt("position")) },
            routineId,
        )

    /** Top set (max weight, then max reps) of the most recent completed workout containing the exercise. */
    fun lastTopSetOf(exerciseId: Long): LastSetRow? =
        jdbc
            .query(
                """
                SELECT s.weight_kg, s.reps, w.date
                FROM sets s JOIN workouts w ON w.id = s.workout_id
                WHERE s.exercise_id = ? AND w.completed
                  AND w.id = (
                    SELECT max(w2.id) FROM sets s2 JOIN workouts w2 ON w2.id = s2.workout_id
                    WHERE s2.exercise_id = ? AND w2.completed
                  )
                ORDER BY s.weight_kg DESC, s.reps DESC LIMIT 1
                """.trimIndent(),
                { rs, _ ->
                    LastSetRow(rs.getBigDecimal("weight_kg"), rs.getInt("reps"), rs.getDate("date").toLocalDate())
                },
                exerciseId,
                exerciseId,
            ).firstOrNull()

    /** Exact match first, then contains; both case/accent-tolerant via unaccent-less ILIKE. */
    fun findExercises(name: String): List<ExerciseRef> {
        val trimmed = name.trim()
        val exact =
            jdbc.query(
                "SELECT id, name FROM exercises WHERE name ILIKE ?",
                { rs, _ -> ExerciseRef(rs.getLong("id"), rs.getString("name")) },
                trimmed,
            )
        if (exact.isNotEmpty()) return exact
        return jdbc.query(
            "SELECT id, name FROM exercises WHERE name ILIKE ? ORDER BY name",
            { rs, _ -> ExerciseRef(rs.getLong("id"), rs.getString("name")) },
            "%$trimmed%",
        )
    }

    fun setsOfWorkout(workoutId: Long): List<SetLine> =
        jdbc.query(
            """
            SELECT e.name, s.weight_kg, s.reps, s.position
            FROM sets s JOIN exercises e ON e.id = s.exercise_id
            WHERE s.workout_id = ? ORDER BY s.position
            """.trimIndent(),
            { rs, _ ->
                SetLine(rs.getString("name"), rs.getDouble("weight_kg"), rs.getInt("reps"), rs.getInt("position"))
            },
            workoutId,
        )

    fun previousCompletedWorkoutOfRoutine(
        routineId: Long,
        beforeWorkoutId: Long,
    ): Long? =
        jdbc
            .query(
                """
                SELECT id FROM workouts WHERE routine_id = ? AND completed AND id <> ?
                ORDER BY date DESC, id DESC LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.getLong("id") },
                routineId,
                beforeWorkoutId,
            ).firstOrNull()

    fun recentWorkouts(limit: Int): List<WorkoutRow> =
        jdbc.query(
            """
            SELECT w.id, w.date, r.name, w.completed
            FROM workouts w JOIN routines r ON r.id = w.routine_id
            ORDER BY w.date DESC, w.id DESC LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                WorkoutRow(
                    rs.getLong("id"),
                    rs.getDate("date").toLocalDate(),
                    rs.getString("name"),
                    rs.getBoolean("completed"),
                )
            },
            limit,
        )
}
