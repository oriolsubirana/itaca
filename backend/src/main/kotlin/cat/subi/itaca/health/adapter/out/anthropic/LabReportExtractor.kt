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
    var date: String? = null
    var value: Double? = null
    var textValue: String? = null
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

        // Multi-page reports can exceed 4K output tokens and truncate the JSON;
        // claude-haiku-4-5 allows up to 64K, 16K is the safe non-streaming ceiling.
        private const val MAX_TOKENS = 16384
        private val EXTRACTION_PROMPT =
            """
            Extract every analyte result from this lab report PDF, from ALL pages.
            For each result return: analyte (exact name as printed), unit, refMin and
            refMax (the reference range bounds, null if absent), and the result itself:
            - value: the number, when the result is numeric.
            - textValue: the literal text, when the result is qualitative
              (e.g. "neg", "pos", "+", "++++", "normal", "mässig"). Leave value null.
            Cumulative reports (Kumulativbefund) print several dated columns per analyte
            (the historical trend in one PDF). When an analyte has multiple values under
            different date columns, emit ONE result per value and set its "date"
            (YYYY-MM-DD) to that column's date. For ordinary single-date reports leave
            each result's "date" null.
            Also return the top-level report date (YYYY-MM-DD, the most recent column for
            cumulative reports) and the laboratory name if present.
            Be exhaustive: include every row of every results table, including the last
            pages. Extract data only; do not interpret or filter anything.
            """.trimIndent()
    }
}
