package cat.subi.itaca.training.application

import cat.subi.itaca.shared.chat.ChatTools
import cat.subi.itaca.training.adapter.out.persistence.SetEntity
import cat.subi.itaca.training.adapter.out.persistence.SetRepository
import cat.subi.itaca.training.adapter.out.persistence.WorkoutEntity
import cat.subi.itaca.training.adapter.out.persistence.WorkoutRepository
import cat.subi.itaca.training.domain.ProgressionPolicy
import cat.subi.itaca.training.domain.Reps
import cat.subi.itaca.training.domain.Weight
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

data class ExercisePlan(
    val exerciseName: String,
    val lastWeightKg: Double?,
    val lastReps: Int?,
    val suggestedWeightKg: Double?,
)

data class StartWorkoutResult(
    val workoutId: Long? = null,
    val routineName: String? = null,
    val date: String? = null,
    val alreadyActive: Boolean = false,
    val plan: List<ExercisePlan> = emptyList(),
    val error: String? = null,
)

data class LogSetResult(
    val confirmed: Boolean,
    val exerciseName: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val setNumberForExercise: Int? = null,
    val suggestedNextWeightKg: Double? = null,
    val exceededTargetWithMargin: Boolean? = null,
    val candidates: List<String>? = null,
    val error: String? = null,
)

data class ExerciseComparison(
    val exerciseName: String,
    val topWeightKg: Double,
    val topReps: Int,
    val previousTopWeightKg: Double?,
    val previousTopReps: Int?,
)

data class EndWorkoutResult(
    val completed: Boolean,
    val routineName: String? = null,
    val totalSets: Int? = null,
    val comparison: List<ExerciseComparison> = emptyList(),
    val error: String? = null,
)

data class WorkoutSummary(
    val date: String,
    val routineName: String,
    val completed: Boolean,
    val sets: List<SetLine>,
)

data class ActivityLine(
    val date: String,
    val type: String,
    val name: String?,
    val distanceKm: Double?,
    val durationMin: Int?,
    val elevationM: Double?,
    val avgHr: Double?,
    val calories: Int?,
)

data class SportTotals(
    val sport: String,
    val thisWeek: String,
    val ytdDistance: String?,
    val ytdElevation: String?,
    val ytdTime: String,
)

data class ActivitiesSummary(
    val connected: Boolean,
    val recent: List<ActivityLine>,
    val totals: List<SportTotals>,
)

/**
 * Chat tools of the training context. Claude reads the descriptions to decide
 * when to call them; all writes are confirmed back as structured data so the
 * model can echo them to the user.
 */
@Service
class TrainingTools(
    private val queries: TrainingQueries,
    private val workouts: WorkoutRepository,
    private val sets: SetRepository,
    private val activityQueries: ActivityQueries,
) : ChatTools {
    private val progression = ProgressionPolicy()

    @Tool(
        name = "start_workout",
        description =
            "Starts a gym workout. Optionally takes the routine name (Push, Pull or Leg); " +
                "if omitted, the next routine in the Push->Pull->Leg rotation is used. " +
                "Returns the planned exercises in order with the last weight/reps and the suggested weight for today.",
    )
    @Transactional
    fun startWorkout(
        @ToolParam(description = "Routine name: Push, Pull or Leg. Omit to follow the rotation", required = false)
        routineName: String?,
    ): StartWorkoutResult {
        workouts.findFirstByCompletedFalseOrderByDateDescIdDesc()?.let { active ->
            return StartWorkoutResult(
                workoutId = active.id,
                routineName = queries.routineNameOf(active.routineId),
                date = active.date.toString(),
                alreadyActive = true,
                plan = planFor(active.routineId),
            )
        }
        val (routineId, resolvedName) =
            routineName
                ?.takeIf { it.isNotBlank() }
                ?.let { queries.routineByName(it) ?: return StartWorkoutResult(error = "Unknown routine: $it") }
                ?: queries.nextRoutineInRotation()
        val workout = workouts.save(WorkoutEntity(date = LocalDate.now(), routineId = routineId))
        return StartWorkoutResult(
            workoutId = workout.id,
            routineName = resolvedName,
            date = workout.date.toString(),
            plan = planFor(routineId),
        )
    }

    @Tool(
        name = "log_set",
        description =
            "Logs one set of the active workout: exercise name (fuzzy matched), weight in kg and reps. " +
                "Returns the confirmation plus the suggested weight for the next session " +
                "(conservative progression: +2.5 kg only after exceeding the 8-rep target with margin).",
    )
    @Transactional
    fun logSet(
        @ToolParam(description = "Exercise name, can be partial (e.g. 'jalón')") exerciseName: String,
        @ToolParam(description = "Weight in kg (0 for bodyweight)") weightKg: Double,
        @ToolParam(description = "Repetitions performed") reps: Int,
        @ToolParam(description = "RPE 1-10 if the user mentions it", required = false) rpe: Double?,
    ): LogSetResult {
        val active =
            workouts.findFirstByCompletedFalseOrderByDateDescIdDesc()
                ?: return LogSetResult(confirmed = false, error = "No active workout. Call start_workout first.")
        val matches = queries.findExercises(exerciseName)
        val exercise =
            when {
                matches.isEmpty() -> return LogSetResult(confirmed = false, error = "Unknown exercise: $exerciseName")
                matches.size > 1 ->
                    return LogSetResult(
                        confirmed = false,
                        candidates = matches.map { it.name },
                        error = "Ambiguous exercise name, ask the user which one",
                    )
                else -> matches.single()
            }
        val activeId = active.id!!
        val weight = Weight.ofKg(BigDecimal.valueOf(weightKg))
        val repetitions = Reps.of(reps)
        val position = sets.countByWorkoutId(activeId) + 1
        sets.save(
            SetEntity(
                workoutId = activeId,
                exerciseId = exercise.id,
                weightKg = weight.kg,
                reps = repetitions.value,
                position = position,
                rpe = rpe?.let { BigDecimal.valueOf(it) },
            ),
        )
        val setNumber = queries.setsOfWorkout(activeId).count { it.exerciseName == exercise.name }
        return LogSetResult(
            confirmed = true,
            exerciseName = exercise.name,
            weightKg = weightKg,
            reps = reps,
            setNumberForExercise = setNumber,
            suggestedNextWeightKg = progression.suggestNextWeight(weight, repetitions).kg.toDouble(),
            exceededTargetWithMargin = repetitions.exceedsWithMargin(Reps.of(TARGET_TOP_REPS)),
        )
    }

    @Tool(
        name = "end_workout",
        description =
            "Ends the active workout, marking it as completed. Optionally records notes. " +
                "Returns a per-exercise comparison (top set) against the previous session of the same routine.",
    )
    @Transactional
    fun endWorkout(
        @ToolParam(description = "Free-text notes for the workout", required = false) notes: String?,
    ): EndWorkoutResult {
        val active =
            workouts.findFirstByCompletedFalseOrderByDateDescIdDesc()
                ?: return EndWorkoutResult(completed = false, error = "No active workout to end.")
        active.completed = true
        notes?.takeIf { it.isNotBlank() }?.let { active.notes = it }
        // Flush so the JdbcTemplate read side below sees the update within this transaction
        workouts.saveAndFlush(active)

        val activeId = active.id!!
        val currentSets = queries.setsOfWorkout(activeId)
        val previousId = queries.previousCompletedWorkoutOfRoutine(active.routineId, activeId)
        val previousTop = previousId?.let { topSetsByExercise(queries.setsOfWorkout(it)) } ?: emptyMap()
        val comparison =
            topSetsByExercise(currentSets).map { (name, top) ->
                ExerciseComparison(
                    exerciseName = name,
                    topWeightKg = top.weightKg,
                    topReps = top.reps,
                    previousTopWeightKg = previousTop[name]?.weightKg,
                    previousTopReps = previousTop[name]?.reps,
                )
            }
        return EndWorkoutResult(
            completed = true,
            routineName = queries.routineNameOf(active.routineId),
            totalSets = currentSets.size,
            comparison = comparison,
        )
    }

    @Tool(
        name = "query_workouts",
        description = "Returns the most recent workouts (date, routine, sets with exercise/weight/reps), newest first.",
    )
    fun queryWorkouts(
        @ToolParam(description = "How many workouts to return (default 5)", required = false) limit: Int?,
    ): List<WorkoutSummary> =
        queries
            .recentWorkouts(limit ?: DEFAULT_QUERY_LIMIT)
            .map { WorkoutSummary(it.date.toString(), it.routineName, it.completed, queries.setsOfWorkout(it.id)) }

    @Tool(
        name = "query_activities",
        description =
            "Returns endurance and gym activities imported from Strava (types: bike, run, hike, gym): " +
                "recent sessions with date, distance (km), duration (min), elevation (m), average heart rate " +
                "and calories, plus per-sport totals for this week and year-to-date (distance, elevation, time). " +
                "Use for ANY question about cycling/running/hiking/gym volume, distance, elevation, pace, " +
                "heart rate or calories. Optionally filter the recent list by sport.",
    )
    fun queryActivities(
        @ToolParam(description = "Optional sport filter: bike, run, hike or gym", required = false) sport: String?,
        @ToolParam(description = "How many recent activities to return (default 10)", required = false) limit: Int?,
    ): ActivitiesSummary {
        val view = activityQueries.view()
        val filter = sport?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val recent =
            view.activities
                .filter { filter == null || it.type == filter }
                .take(limit ?: DEFAULT_ACTIVITY_LIMIT)
                .map {
                    ActivityLine(
                        date = it.date,
                        type = it.type,
                        name = it.name,
                        distanceKm = it.distanceKm,
                        durationMin = it.durationS?.let { s -> s / SECONDS_PER_MINUTE },
                        elevationM = it.elevationM,
                        avgHr = it.avgHr,
                        calories = it.calories,
                    )
                }
        val totals =
            view.volume.map { (sportKey, v) ->
                SportTotals(
                    sport = sportKey,
                    thisWeek = "${v.weeks.last().value} ${v.unit}".replace('.', ','),
                    ytdDistance = v.ytd.distance,
                    ytdElevation = v.ytd.elevation,
                    ytdTime = v.ytd.time,
                )
            }
        return ActivitiesSummary(view.connected, recent, totals)
    }

    private fun planFor(routineId: Long): List<ExercisePlan> =
        queries.exercisesOfRoutine(routineId).map { row ->
            val last = queries.lastTopSetOf(row.exerciseId)
            ExercisePlan(
                exerciseName = row.exerciseName,
                lastWeightKg = last?.weightKg?.toDouble(),
                lastReps = last?.reps,
                suggestedWeightKg =
                    last
                        ?.let { progression.suggestNextWeight(Weight.ofKg(it.weightKg), Reps.of(it.reps)) }
                        ?.kg
                        ?.toDouble(),
            )
        }

    private fun topSetsByExercise(lines: List<SetLine>): Map<String, SetLine> =
        lines
            .groupBy { it.exerciseName }
            .mapValues { (_, sets) -> sets.maxWith(compareBy({ it.weightKg }, { it.reps })) }

    companion object {
        private const val TARGET_TOP_REPS = 8
        private const val DEFAULT_QUERY_LIMIT = 5
        private const val DEFAULT_ACTIVITY_LIMIT = 10
        private const val SECONDS_PER_MINUTE = 60
    }
}
