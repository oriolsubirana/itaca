// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.AnalyteRef
import cat.subi.itaca.health.application.AnalyteSeries
import cat.subi.itaca.health.application.LabResultQueries
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health/analytes")
class AnalyteController(
    private val queries: LabResultQueries,
) {
    @GetMapping
    fun analytesWithData(): List<AnalyteRef> = queries.analytesWithData()

    @GetMapping("/{code}/series")
    fun series(
        @PathVariable code: String,
    ): AnalyteSeries = queries.seriesByCode(code) ?: throw NoSuchElementException("Unknown analyte: $code")

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
