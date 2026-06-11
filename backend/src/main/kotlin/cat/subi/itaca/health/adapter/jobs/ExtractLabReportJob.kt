package cat.subi.itaca.health.adapter.jobs

import cat.subi.itaca.health.application.LabReportService
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component

/**
 * JobRunr request/handler pair for the async PDF extraction. The request is
 * serialized to the jobs table; JobRunr retries on failure (default policy)
 * and the extraction itself is idempotent.
 */
class ExtractLabReportRequest(
    var reportId: Long = 0,
) : JobRequest {
    override fun getJobRequestHandler(): Class<ExtractLabReportHandler> = ExtractLabReportHandler::class.java
}

@Component
class ExtractLabReportHandler(
    private val labReports: LabReportService,
) : JobRequestHandler<ExtractLabReportRequest> {
    override fun run(request: ExtractLabReportRequest) {
        labReports.runExtraction(request.reportId)
    }
}
