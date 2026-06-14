// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.ActivitiesView
import cat.subi.itaca.training.application.ActivityQueries
import cat.subi.itaca.training.application.ExerciseProgressionDto
import cat.subi.itaca.training.application.ExerciseRef
import cat.subi.itaca.training.application.SessionDetailDto
import cat.subi.itaca.training.application.SessionSummaryDto
import cat.subi.itaca.training.application.TrainingHistoryQueries
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Read-only history and progression for the Gym dashboard. */
@RestController
@RequestMapping("/api/training")
class TrainingHistoryController(
    private val queries: TrainingHistoryQueries,
    private val activityQueries: ActivityQueries,
) {
    @GetMapping("/sessions")
    fun sessions(): List<SessionSummaryDto> = queries.sessions()

    @GetMapping("/sessions/{id}")
    fun sessionDetail(
        @PathVariable id: Long,
    ): SessionDetailDto = queries.sessionDetail(id)

    @GetMapping("/exercises")
    fun exercises(): List<ExerciseRef> = queries.exercises()

    @GetMapping("/exercises/{id}/progression")
    fun progression(
        @PathVariable id: Long,
    ): ExerciseProgressionDto = queries.exerciseProgression(id)

    @GetMapping("/activities")
    fun activities(): ActivitiesView = activityQueries.view()

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
