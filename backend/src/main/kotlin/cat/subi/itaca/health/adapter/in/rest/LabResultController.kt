// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.LabReportService
import cat.subi.itaca.health.application.LabResultDto
import cat.subi.itaca.health.application.LabResultUpdate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Row-level corrections during the review of an extracted lab report. */
@RestController
@RequestMapping("/api/health")
class LabResultController(
    private val labReports: LabReportService,
) {
    @PatchMapping("/lab-results/{id}")
    fun updateResult(
        @PathVariable id: Long,
        @RequestBody update: LabResultUpdate,
    ): LabResultDto = labReports.updateResult(id, update)

    @DeleteMapping("/lab-results/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteResult(
        @PathVariable id: Long,
    ) = labReports.deleteResult(id)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
