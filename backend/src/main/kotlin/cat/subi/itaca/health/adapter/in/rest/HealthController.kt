// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.DiaryEntryDto
import cat.subi.itaca.health.application.DiaryEntryUpdate
import cat.subi.itaca.health.application.FlareDto
import cat.subi.itaca.health.application.HealthSummary
import cat.subi.itaca.health.application.HealthTools
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

data class StartFlareRequest(
    val severity: String,
    val date: String? = null,
    val notes: String? = null,
)

data class EndFlareRequest(
    val date: String? = null,
)

data class FlaresView(
    val active: FlareDto?,
    val recent: List<FlareDto>,
)

@RestController
@RequestMapping("/api/health")
class HealthController(
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

    @GetMapping("/flares")
    fun flares(): FlaresView =
        FlaresView(
            active = health.queryHealth(0).activeFlare,
            recent = health.recentFlares(),
        )

    @PostMapping("/flares/start")
    fun startFlare(
        @RequestBody request: StartFlareRequest,
    ): FlareDto {
        val result = health.logFlare("start", request.severity, request.date, request.notes)
        return result.flare ?: throw IllegalArgumentException(result.error)
    }

    @PostMapping("/flares/end")
    fun endFlare(
        @RequestBody request: EndFlareRequest,
    ): FlareDto {
        val result = health.logFlare("end", null, request.date, null)
        return result.flare ?: throw IllegalArgumentException(result.error)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
