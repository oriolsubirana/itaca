package cat.subi.itaca.health.adapter.jobs

import cat.subi.itaca.health.application.MedicalDocumentService
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component

/**
 * JobRunr request/handler pair for the async clinical-document extraction. The
 * request is serialized to the jobs table; JobRunr retries on failure and the
 * extraction itself is idempotent.
 */
class ExtractMedicalDocumentRequest(
    var documentId: Long = 0,
) : JobRequest {
    override fun getJobRequestHandler() = ExtractMedicalDocumentHandler::class.java
}

@Component
class ExtractMedicalDocumentHandler(
    private val documents: MedicalDocumentService,
) : JobRequestHandler<ExtractMedicalDocumentRequest> {
    override fun run(request: ExtractMedicalDocumentRequest) {
        documents.runExtraction(request.documentId)
    }
}
