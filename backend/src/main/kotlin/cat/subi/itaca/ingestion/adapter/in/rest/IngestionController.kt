// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.ingestion.adapter.`in`.rest

import cat.subi.itaca.ingestion.adapter.jobs.ProcessIngestionRequest
import cat.subi.itaca.ingestion.application.IngestedFileDto
import cat.subi.itaca.ingestion.application.IngestionService
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Generic intake endpoint. An iOS Shortcut (or the web inbox) POSTs any PDF/CSV here;
 * the file is stored and queued, then classified and routed to the owning context.
 */
@RestController
@RequestMapping("/api/ingest")
class IngestionController(
    private val ingestion: IngestionService,
    private val jobs: JobRequestScheduler,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun ingest(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("source", required = false) source: String?,
    ): IngestedFileDto {
        require(!file.isEmpty) { "Empty file" }
        val dto = ingestion.ingest(source ?: "manual", file.originalFilename ?: "archivo", file.bytes)
        jobs.enqueue(ProcessIngestionRequest(dto.id))
        return dto
    }

    @GetMapping
    fun inbox(): List<IngestedFileDto> = ingestion.recent()

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(
        @PathVariable id: Long,
    ): IngestedFileDto {
        val dto = ingestion.retry(id)
        jobs.enqueue(ProcessIngestionRequest(id))
        return dto
    }

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
