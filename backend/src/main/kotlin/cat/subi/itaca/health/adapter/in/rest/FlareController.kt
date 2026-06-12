// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.FlareDto
import cat.subi.itaca.health.application.FlareUpdate
import cat.subi.itaca.health.application.HealthTools
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
@RequestMapping("/api/health/flares")
class FlareController(
    private val health: HealthTools,
) {
    @GetMapping
    fun flares(): FlaresView =
        FlaresView(
            active = health.queryHealth(0).activeFlare,
            recent = health.recentFlares(),
        )

    @PostMapping("/start")
    fun startFlare(
        @RequestBody request: StartFlareRequest,
    ): FlareDto {
        val result = health.logFlare("start", request.severity, request.date, request.notes)
        return result.flare ?: throw IllegalArgumentException(result.error)
    }

    @PostMapping("/end")
    fun endFlare(
        @RequestBody request: EndFlareRequest,
    ): FlareDto {
        val result = health.logFlare("end", null, request.date, null)
        return result.flare ?: throw IllegalArgumentException(result.error)
    }

    @PatchMapping("/{id}")
    fun updateFlare(
        @PathVariable id: Long,
        @RequestBody update: FlareUpdate,
    ): FlareDto = health.updateFlare(id, update)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFlare(
        @PathVariable id: Long,
    ) = health.deleteFlare(id)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
