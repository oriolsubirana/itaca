// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.adapter.jobs.ExtractLabReportRequest
import cat.subi.itaca.health.application.LabReportDetail
import cat.subi.itaca.health.application.LabReportDto
import cat.subi.itaca.health.application.LabReportService
import cat.subi.itaca.health.application.LabResultDto
import cat.subi.itaca.health.application.LabResultUpdate
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/health")
class LabController(
    private val labReports: LabReportService,
    private val jobs: JobRequestScheduler,
) {
    /** Accepts one or many PDFs; each becomes its own report with its own extraction job. */
    @PostMapping("/lab-reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upload(
        @RequestParam("files") files: List<MultipartFile>,
    ): List<LabReportDto> {
        require(files.isNotEmpty()) { "No files uploaded" }
        require(files.none { it.isEmpty }) { "Empty file in upload" }
        return files.map { file ->
            val report = labReports.upload(file.originalFilename ?: "informe.pdf", file.bytes)
            jobs.enqueue(ExtractLabReportRequest(report.id))
            report
        }
    }

    @GetMapping("/lab-reports")
    fun reports(): List<LabReportDto> = labReports.recentReports()

    @GetMapping("/lab-reports/{id}")
    fun detail(
        @PathVariable id: Long,
    ): LabReportDetail = labReports.detail(id)

    @PostMapping("/lab-reports/{id}/confirm")
    fun confirm(
        @PathVariable id: Long,
    ): LabReportDto = labReports.review(id, confirm = true)

    @PostMapping("/lab-reports/{id}/discard")
    fun discard(
        @PathVariable id: Long,
    ): LabReportDto = labReports.review(id, confirm = false)

    @DeleteMapping("/lab-reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReport(
        @PathVariable id: Long,
    ) = labReports.deleteReport(id)

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
