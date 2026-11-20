@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.TrainingLoadService
import cat.subi.itaca.training.application.TrainingLoadView
import cat.subi.itaca.training.application.TrainingWeekService
import cat.subi.itaca.training.application.TrainingWeekView
import cat.subi.itaca.training.application.TriathlonPlanService
import cat.subi.itaca.training.application.TriathlonPlanView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class TriathlonPlanController(
    private val plan: TriathlonPlanService,
    private val weeks: TrainingWeekService,
    private val load: TrainingLoadService,
) {
    @GetMapping("/api/training/plan")
    fun plan(): TriathlonPlanView = plan.view(LocalDate.now())

    /** The plan week containing [date] (default: this week), with each session's status; 404 outside the plan. */
    @GetMapping("/api/training/plan/week")
    fun week(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    ): ResponseEntity<TrainingWeekView> {
        val today = LocalDate.now()
        return weeks.week(date ?: today, today)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/api/training/load")
    fun load(
        @RequestParam(defaultValue = "42") days: Int,
    ): TrainingLoadView = load.view(LocalDate.now(), days)
}
