package cat.subi.itaca.ingestion.adapter.jobs

import cat.subi.itaca.ingestion.application.IngestionService
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component

/**
 * JobRunr request/handler pair for the async classify-and-route step. Serialized to
 * the jobs table; JobRunr retries on failure and [IngestionService.process] is idempotent
 * (re-running re-classifies and re-publishes the routing event).
 */
class ProcessIngestionRequest(
    var ingestionId: Long = 0,
) : JobRequest {
    override fun getJobRequestHandler(): Class<ProcessIngestionHandler> = ProcessIngestionHandler::class.java
}

@Component
class ProcessIngestionHandler(
    private val ingestion: IngestionService,
) : JobRequestHandler<ProcessIngestionRequest> {
    override fun run(request: ProcessIngestionRequest) {
        ingestion.process(request.ingestionId)
    }
}
