// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.adapter.jobs.ExtractMedicalDocumentRequest
import cat.subi.itaca.health.application.MedicalDocumentDetail
import cat.subi.itaca.health.application.MedicalDocumentDto
import cat.subi.itaca.health.application.MedicalDocumentService
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
class MedicalDocumentController(
    private val medicalDocuments: MedicalDocumentService,
    private val jobs: JobRequestScheduler,
) {
    /** Accepts one or many clinical PDFs; each becomes its own document with its own job. */
    @PostMapping("/medical-documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upload(
        @RequestParam("files") files: List<MultipartFile>,
    ): List<MedicalDocumentDto> {
        require(files.isNotEmpty()) { "No files uploaded" }
        require(files.none { it.isEmpty }) { "Empty file in upload" }
        return files.map { file ->
            val document = medicalDocuments.upload(file.originalFilename ?: "documento.pdf", file.bytes)
            jobs.enqueue(ExtractMedicalDocumentRequest(document.id))
            document
        }
    }

    @GetMapping("/medical-documents")
    fun documents(): List<MedicalDocumentDto> = medicalDocuments.recentDocuments()

    @GetMapping("/medical-documents/{id}")
    fun detail(
        @PathVariable id: Long,
    ): MedicalDocumentDetail = medicalDocuments.detail(id)

    /** Serves the original PDF inline so the UI can display the source document. */
    @GetMapping("/medical-documents/{id}/file")
    fun file(
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val file = medicalDocuments.loadFile(id)
        val disposition = ContentDisposition.inline().filename(file.filename).build()
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(file.content)
    }

    @PostMapping("/medical-documents/{id}/extract")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reextract(
        @PathVariable id: Long,
    ): MedicalDocumentDto {
        val document = medicalDocuments.prepareReextraction(id)
        jobs.enqueue(ExtractMedicalDocumentRequest(id))
        return document
    }

    @PostMapping("/medical-documents/{id}/confirm")
    fun confirm(
        @PathVariable id: Long,
    ): MedicalDocumentDto = medicalDocuments.review(id, confirm = true)

    @PostMapping("/medical-documents/{id}/discard")
    fun discard(
        @PathVariable id: Long,
    ): MedicalDocumentDto = medicalDocuments.review(id, confirm = false)

    @DeleteMapping("/medical-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDocument(
        @PathVariable id: Long,
    ) = medicalDocuments.deleteDocument(id)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
