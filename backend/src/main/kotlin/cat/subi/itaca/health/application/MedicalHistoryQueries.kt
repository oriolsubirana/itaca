package cat.subi.itaca.health.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class MedicalEncounter(
    val date: String?,
    val type: String?,
    val center: String?,
    val diagnoses: List<String>,
    val medications: List<String>,
)

data class MedicalHistory(
    val encounters: List<MedicalEncounter>,
)

/**
 * Read side of the clinical documents: recorded encounters (diagnoses + medications)
 * from CONFIRMED documents only. Exposed to the chat as query_medical_history. Returns
 * facts as recorded; it never interprets them.
 */
@Service
class MedicalHistoryQueries(
    private val jdbc: JdbcTemplate,
) : ChatTools {
    @Tool(
        name = "query_medical_history",
        description =
            "Returns recorded clinical encounters (date, type, center, diagnoses and prescribed " +
                "medications) from confirmed medical documents. Optionally filter by a term that " +
                "matches the document text, type, center, a diagnosis or a medication. These are " +
                "recorded facts — describe them, never interpret a diagnosis.",
    )
    fun queryMedicalHistory(
        @ToolParam(
            required = false,
            description = "Optional search term, e.g. 'colitis' or 'amoxicilina'. Empty returns recent history.",
        )
        query: String?,
    ): MedicalHistory {
        val term = query?.trim().orEmpty()
        val ids = if (term.isEmpty()) recentDocumentIds() else matchingDocumentIds("%$term%")
        return MedicalHistory(ids.map { encounter(it) })
    }

    private fun recentDocumentIds(): List<Long> =
        jdbc.queryForList(
            """
            SELECT id FROM medical_documents
            WHERE status = 'confirmed'
            ORDER BY doc_date DESC NULLS LAST, created_at DESC
            LIMIT 20
            """.trimIndent(),
            Long::class.java,
        )

    private fun matchingDocumentIds(like: String): List<Long> =
        jdbc.queryForList(
            """
            SELECT d.id FROM medical_documents d
            WHERE d.status = 'confirmed' AND (
                d.full_text ILIKE ? OR d.type ILIKE ? OR d.center ILIKE ?
                OR EXISTS (
                    SELECT 1 FROM medical_diagnoses dx
                    WHERE dx.document_id = d.id AND (dx.label ILIKE ? OR dx.code ILIKE ?)
                )
                OR EXISTS (
                    SELECT 1 FROM medical_medications m
                    WHERE m.document_id = d.id AND m.name ILIKE ?
                )
            )
            ORDER BY d.doc_date DESC NULLS LAST, d.created_at DESC
            LIMIT 20
            """.trimIndent(),
            Long::class.java,
            like,
            like,
            like,
            like,
            like,
            like,
        )

    private fun encounter(documentId: Long): MedicalEncounter {
        val header =
            jdbc.queryForObject(
                "SELECT doc_date, type, center FROM medical_documents WHERE id = ?",
                { rs, _ ->
                    Triple(
                        rs.getDate("doc_date")?.toLocalDate()?.toString(),
                        rs.getString("type"),
                        rs.getString("center"),
                    )
                },
                documentId,
            )!!
        val diagnoses =
            jdbc.query(
                "SELECT code, label FROM medical_diagnoses WHERE document_id = ? ORDER BY id",
                { rs, _ ->
                    val code = rs.getString("code")
                    rs.getString("label") + if (code != null) " ($code)" else ""
                },
                documentId,
            )
        val medications =
            jdbc.query(
                "SELECT name, dose, schedule FROM medical_medications WHERE document_id = ? ORDER BY id",
                { rs, _ ->
                    listOfNotNull(rs.getString("name"), rs.getString("dose"), rs.getString("schedule"))
                        .joinToString(" ")
                },
                documentId,
            )
        return MedicalEncounter(header.first, header.second, header.third, diagnoses, medications)
    }
}
