// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.adapter.jobs.ExtractLabReportRequest
import cat.subi.itaca.health.application.LabReportDetail
import cat.subi.itaca.health.application.LabReportDto
import cat.subi.itaca.health.application.LabReportService
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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

    /** Serves the original PDF inline so the UI can display the source document. */
    @GetMapping("/lab-reports/{id}/file")
    fun file(
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val file = labReports.loadFile(id)
        val disposition = ContentDisposition.inline().filename(file.filename).build()
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(file.content)
    }

    @PostMapping("/lab-reports/{id}/confirm")
    fun confirm(
        @PathVariable id: Long,
    ): LabReportDto = labReports.review(id, confirm = true)

    /** Re-runs the extraction (e.g. after a 0-result run or a prompt improvement). */
    @PostMapping("/lab-reports/{id}/extract")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reextract(
        @PathVariable id: Long,
    ): LabReportDto {
        val report = labReports.prepareReextraction(id)
        jobs.enqueue(ExtractLabReportRequest(id))
        return report
    }

    @PostMapping("/lab-reports/{id}/discard")
    fun discard(
        @PathVariable id: Long,
    ): LabReportDto = labReports.review(id, confirm = false)

    @DeleteMapping("/lab-reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReport(
        @PathVariable id: Long,
    ) = labReports.deleteReport(id)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
