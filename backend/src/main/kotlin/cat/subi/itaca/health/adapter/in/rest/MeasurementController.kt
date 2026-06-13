// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.AnalyteSeries
import cat.subi.itaca.health.application.LabResultQueries
import cat.subi.itaca.health.application.MeasurementRef
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Chartable measurements: dictionary analytes and un-normalized results alike,
 * so any loaded numeric value can be plotted. Keyed by an opaque string passed
 * as a query parameter (keys contain ':' and raw analyte names).
 */
@RestController
@RequestMapping("/api/health/measurements")
class MeasurementController(
    private val queries: LabResultQueries,
) {
    @GetMapping
    fun measurements(): List<MeasurementRef> = queries.chartableMeasurements()

    @GetMapping("/series")
    fun series(
        @RequestParam key: String,
    ): AnalyteSeries = queries.seriesByKey(key) ?: throw NoSuchElementException("Unknown measurement: $key")

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
