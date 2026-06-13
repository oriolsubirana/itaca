// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.TrainingQueries
import cat.subi.itaca.training.application.TrainingSummaryDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Read-only glance for the Home dashboard: last session and next routine. */
@RestController
@RequestMapping("/api/training")
class TrainingSummaryController(
    private val queries: TrainingQueries,
) {
    @GetMapping("/summary")
    fun summary(): TrainingSummaryDto = queries.homeSummary()
}
