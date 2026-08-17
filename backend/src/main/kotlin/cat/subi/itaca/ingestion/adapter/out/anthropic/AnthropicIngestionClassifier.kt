package cat.subi.itaca.ingestion.adapter.out.anthropic

import cat.subi.itaca.ingestion.application.IngestionClassifierAi
import cat.subi.itaca.ingestion.domain.Destination
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/** The model's verdict for an ambiguous PDF. */
class ClassificationResponse {
    var kind: String = ""
}

/**
 * AI tier of the routing: a cheap claude-haiku call that looks at the PDF and decides
 * whether it is a medical lab report or a financial/pension statement. Only reached when
 * the deterministic rules cannot tell (a PDF with an uninformative filename).
 */
@Component
class AnthropicIngestionClassifier(
    private val chatClient: ChatClient,
    @Value("\${itaca.ingestion.classifier-model:claude-haiku-4-5}") private val model: String,
) : IngestionClassifierAi {
    override fun classify(
        filename: String,
        content: ByteArray,
    ): Destination {
        val response =
            chatClient
                .prompt()
                .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
                .user {
                    it.text(PROMPT).media(PDF, ByteArrayResource(content))
                }.call()
                .entity(ClassificationResponse::class.java) ?: return Destination.UNKNOWN
        return when (response.kind.trim().uppercase()) {
            "LAB" -> Destination.HEALTH_LAB
            "DOCUMENT" -> Destination.HEALTH_DOCUMENT
            "FINANCE" -> Destination.FINANCE_BANK
            else -> Destination.UNKNOWN
        }
    }

    private companion object {
        val PDF = MimeType.valueOf("application/pdf")
        const val MAX_TOKENS = 64
        val PROMPT =
            """
            Look at this PDF and decide what kind of document it is, for routing into a personal
            dashboard. Answer with a single field `kind`:
            - "LAB" if it is a lab / analytics report with numeric test results (blood test,
              laboratory values, analytics).
            - "DOCUMENT" if it is another medical / clinical document without lab values (consultation
              letter, diagnosis or specialist report, treatment plan, prescription, discharge summary).
            - "FINANCE" if it is a financial document (bank statement, pension/investment performance
              report such as finpension).
            - "UNKNOWN" if it is clearly none of these.
            """.trimIndent()
    }
}
