// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.TrainingQueries
import cat.subi.itaca.training.application.TrainingSummaryDto
import cat.subi.itaca.training.application.TrainingWeekService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Read-only glance for the Home dashboard: last session, next routine and today's plan load. */
@RestController
@RequestMapping("/api/training")
class TrainingSummaryController(
    private val queries: TrainingQueries,
    private val weeks: TrainingWeekService,
) {
    @GetMapping("/summary")
    fun summary(): TrainingSummaryDto {
        val today = LocalDate.now()
        val plan = weeks.today(today)
        val summary = queries.homeSummary()
        return summary.copy(
            // The triathlon plan pins the gym routine; the Push/Pull/Leg rotation is the fallback.
            nextRoutine = weeks.nextPlannedGymRoutine(today) ?: summary.nextRoutine,
            todayPlannedKcal = plan.pendingKcal,
            todayDayLoad = plan.dayLoad,
        )
    }
}
