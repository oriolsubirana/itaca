package cat.subi.itaca.ingestion.domain

/** The recognised content types an ingested file can have. */
enum class FileType {
    PDF,
    CSV,
    UNKNOWN,
}

/** Which bounded context an ingested file is routed to. */
enum class Destination {
    HEALTH_LAB,

    /** A clinical document (consultation letter, report, treatment plan, prescription) — not a lab report. */
    HEALTH_DOCUMENT,
    FINANCE_BANK,

    /** Could not be decided deterministically — the AI tier must classify it. */
    UNKNOWN,
}

/** Detects the content type from magic bytes (authoritative) falling back to the extension. */
object FileTypeDetector {
    private val PDF_MAGIC = "%PDF".toByteArray()

    fun detect(
        filename: String,
        content: ByteArray,
    ): FileType {
        if (content.size >= PDF_MAGIC.size && content.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)) {
            return FileType.PDF
        }
        val name = filename.lowercase()
        return when {
            name.endsWith(".pdf") -> FileType.PDF
            name.endsWith(".csv") -> FileType.CSV
            else -> FileType.UNKNOWN
        }
    }
}

/**
 * Tier 1 of routing: cheap, deterministic rules over the file type and name.
 * A CSV can only be a Neon statement; PDFs are split by well-known name markers,
 * and anything ambiguous is left UNKNOWN for the AI tier to classify.
 */
object IngestionRouter {
    private val FINANCE_MARKERS = listOf("finpension", "performance")
    private val LAB_MARKERS = listOf("analitica", "analítica", "analisis", "análisis", "hemogram", "sangre", "blood")

    fun route(
        filename: String,
        type: FileType,
    ): Destination {
        val name = filename.lowercase()
        return when (type) {
            FileType.CSV -> Destination.FINANCE_BANK
            FileType.PDF ->
                when {
                    FINANCE_MARKERS.any { name.contains(it) } -> Destination.FINANCE_BANK
                    LAB_MARKERS.any { name.contains(it) } -> Destination.HEALTH_LAB
                    else -> Destination.UNKNOWN
                }
            FileType.UNKNOWN -> Destination.UNKNOWN
        }
    }
}
