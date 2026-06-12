// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.DiaryEntryDto
import cat.subi.itaca.health.application.DiaryEntryUpdate
import cat.subi.itaca.health.application.HealthSummary
import cat.subi.itaca.health.application.HealthTools
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class DiaryEntryRequest(
    val bristol: Int? = null,
    val pain: Int? = null,
    val urgency: Int? = null,
    val blood: Boolean? = null,
    val bowelMovements: Int? = null,
    val stress: Int? = null,
    val notes: String? = null,
)

@RestController
@RequestMapping("/api/health")
class DiaryController(
    private val health: HealthTools,
) {
    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) days: Int?,
    ): HealthSummary = health.queryHealth(days)

    @GetMapping("/diary/{date}")
    fun entry(
        @PathVariable date: LocalDate,
    ): DiaryEntryDto = health.entryOf(date) ?: DiaryEntryDto(date.toString(), null, null, null, false, null, null, null)

    @PutMapping("/diary/{date}")
    fun upsertEntry(
        @PathVariable date: LocalDate,
        @RequestBody request: DiaryEntryRequest,
    ): DiaryEntryDto =
        health.upsert(
            DiaryEntryUpdate(
                date = date,
                bristol = request.bristol,
                pain = request.pain,
                urgency = request.urgency,
                blood = request.blood,
                bowelMovements = request.bowelMovements,
                stress = request.stress,
                notes = request.notes,
            ),
        )

    @DeleteMapping("/diary/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(
        @PathVariable date: LocalDate,
    ) = health.deleteEntry(date)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
