package cat.subi.itaca.ingestion.application

import java.time.Instant

/** An ingested file as persisted in `ingested_files`. */
data class IngestedFile(
    val id: Long,
    val source: String,
    val name: String,
    val type: String,
    val destination: String?,
    val status: String,
    val storagePath: String,
    val errorMessage: String?,
    val detail: String?,
    val createdAt: Instant,
) {
    fun toDto(): IngestedFileDto =
        IngestedFileDto(
            id = id,
            name = name,
            type = type,
            destination = destination,
            status = status,
            errorMessage = errorMessage,
            detail = detail,
            createdAt = createdAt.toString(),
        )
}

/** What the inbox UI sees (no storage path, no raw bytes). */
data class IngestedFileDto(
    val id: Long,
    val name: String,
    val type: String,
    val destination: String?,
    val status: String,
    val errorMessage: String?,
    val detail: String?,
    val createdAt: String,
)
