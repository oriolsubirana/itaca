package cat.subi.itaca.health.adapter.out.anthropic

import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/** One extracted analyte row, exactly as printed on the report. */
class ExtractedResult {
    var analyte: String = ""
    var value: Double? = null
    var unit: String? = null
    var refMin: Double? = null
    var refMax: Double? = null
}

class LabExtraction {
    var date: String? = null
    var laboratory: String? = null
    var results: List<ExtractedResult> = emptyList()
}

/**
 * Extracts structured lab results from a PDF using claude-haiku-4-5 with
 * structured output. Returns data only — no interpretation.
 */
@Component
class LabReportExtractor(
    private val chatClient: ChatClient,
    @Value("\${itaca.extraction.model:claude-haiku-4-5}") private val model: String,
) {
    fun extract(pdf: ByteArray): LabExtraction =
        chatClient
            .prompt()
            .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
            .user { user ->
                user
                    .text(EXTRACTION_PROMPT)
                    .media(PDF, ByteArrayResource(pdf))
            }.call()
            .entity(LabExtraction::class.java)!!

    companion object {
        private val PDF = MimeType.valueOf("application/pdf")
        private const val MAX_TOKENS = 4096
        private val EXTRACTION_PROMPT =
            """
            Extract every analyte result from this lab report PDF.
            For each result return: analyte (exact name as printed), value (numeric),
            unit, refMin and refMax (the reference range bounds, null if absent).
            Also return the report date (YYYY-MM-DD) and the laboratory name if present.
            Extract data only; do not interpret or filter anything.
            """.trimIndent()
    }
}
