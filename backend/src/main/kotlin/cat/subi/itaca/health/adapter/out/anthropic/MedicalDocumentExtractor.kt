package cat.subi.itaca.health.adapter.out.anthropic

import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/** One diagnosis exactly as recorded on the document (code optional). */
class ExtractedDiagnosis {
    var code: String? = null
    var label: String = ""
    var date: String? = null
}

/** One prescribed/active medication as recorded on the document. */
class ExtractedMedication {
    var name: String = ""
    var dose: String? = null
    var schedule: String? = null
    var duration: String? = null
    var reason: String? = null
}

class MedicalExtraction {
    var date: String? = null
    var type: String? = null
    var provider: String? = null
    var center: String? = null
    var fullText: String? = null
    var diagnoses: List<ExtractedDiagnosis> = emptyList()
    var medications: List<ExtractedMedication> = emptyList()
}

/**
 * Extracts recorded facts from a narrative clinical document (discharge summary,
 * consultation note...) using claude-haiku-4-5 with structured output. Records
 * data only — it must never interpret diagnoses or give medical advice.
 */
@Component
class MedicalDocumentExtractor(
    private val chatClient: ChatClient,
    @Value("\${itaca.extraction.model:claude-haiku-4-5}") private val model: String,
) {
    fun extract(pdf: ByteArray): MedicalExtraction =
        chatClient
            .prompt()
            .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
            .user { user ->
                user
                    .text(EXTRACTION_PROMPT)
                    .media(PDF, ByteArrayResource(pdf))
            }.call()
            .entity(MedicalExtraction::class.java)!!

    companion object {
        private val PDF = MimeType.valueOf("application/pdf")
        private const val MAX_TOKENS = 16384
        private val EXTRACTION_PROMPT =
            """
            This is a clinical document (discharge summary, consultation, report...).
            Extract the RECORDED FACTS only. Do NOT interpret, diagnose, judge or add
            anything that is not written in the document.
            Return:
            - date: the encounter or document date (YYYY-MM-DD).
            - type: the kind of document/encounter as printed (e.g. "Urgencias",
              "Alta hospitalaria", "Consulta"), in its original language.
            - provider: the issuing clinician or service, if present.
            - center: the hospital/clinic name, if present.
            - diagnoses: each diagnosis as written — code (e.g. an ICD code like "J0390"
              if present, else null), label (the literal text), date (YYYY-MM-DD if the
              document states when it was diagnosed, else null). Include relevant
              recorded antecedents (e.g. a chronic condition mentioned in the history).
            - medications: each drug as written — name, dose, schedule (posology),
              duration, reason (if stated).
            - fullText: the document's body text, cleaned of headers/footers, preserving
              the clinical narrative so it can be searched later.
            Be faithful to the source; leave fields null when absent.
            """.trimIndent()
    }
}
