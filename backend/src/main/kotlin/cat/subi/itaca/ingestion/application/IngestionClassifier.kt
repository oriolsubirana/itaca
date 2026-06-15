package cat.subi.itaca.ingestion.application

import cat.subi.itaca.ingestion.domain.Destination
import cat.subi.itaca.ingestion.domain.FileType
import cat.subi.itaca.ingestion.domain.IngestionRouter
import org.springframework.stereotype.Component

/** AI tier port: classifies an ambiguous PDF when the deterministic rules cannot decide. */
interface IngestionClassifierAi {
    fun classify(
        filename: String,
        content: ByteArray,
    ): Destination
}

/**
 * Two-tier routing: the cheap deterministic [IngestionRouter] first; only the
 * ambiguous PDF long tail falls through to the AI tier.
 */
@Component
class IngestionClassifier(
    private val ai: IngestionClassifierAi,
) {
    fun classify(
        filename: String,
        type: FileType,
        content: ByteArray,
    ): Destination {
        val deterministic = IngestionRouter.route(filename, type)
        if (deterministic != Destination.UNKNOWN) return deterministic
        // Only PDFs are worth an AI round-trip; unknown binary types stay unknown.
        return if (type == FileType.PDF) ai.classify(filename, content) else Destination.UNKNOWN
    }
}
