package cat.subi.itaca.google.adapter.jobs

import cat.subi.itaca.google.application.DriveSyncService
import jakarta.annotation.PostConstruct
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** JobRunr request/handler for one Drive folder poll. The poll is idempotent (dedupe by file id). */
class DriveSyncRequest : JobRequest {
    override fun getJobRequestHandler(): Class<DriveSyncHandler> = DriveSyncHandler::class.java
}

@Component
class DriveSyncHandler(
    private val sync: DriveSyncService,
) : JobRequestHandler<DriveSyncRequest> {
    override fun run(request: DriveSyncRequest) {
        sync.sync()
    }
}

/**
 * Registers the recurring folder poll, but only when a folder is configured (GOOGLE_DRIVE_FOLDER_ID)
 * — so local/test runs without it never schedule anything.
 */
@Component
@ConditionalOnProperty(name = ["GOOGLE_DRIVE_FOLDER_ID"])
class DriveSyncScheduler(
    private val jobs: JobRequestScheduler,
) {
    @PostConstruct
    fun register() {
        jobs.scheduleRecurrently("drive-folder-sync", "*/5 * * * *", DriveSyncRequest())
    }
}
