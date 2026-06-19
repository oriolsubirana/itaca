package cat.subi.itaca.nutrition.adapter.out.anthropic

import cat.subi.itaca.nutrition.application.MealAnalysis
import cat.subi.itaca.nutrition.application.MealAnalyzer
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/** Structured-output target for the analysis (var + defaults so any Jackson can bind it). */
class MealAnalysisResponse {
    var description: String = ""
    var calories: Int? = null
    var macros: String? = null
    var mealType: String = "lunch"
    var onPlan: Boolean = true
}

/**
 * Estimates a meal's description, calories, macros, type and anti-inflammatory paleo adherence —
 * from a photo (Claude vision) or from a free-text description. Estimates are approximate (not a
 * medical figure) and the user reviews them before saving.
 */
@Component
class AnthropicMealAnalyzer(
    private val chatClient: ChatClient,
    @Value("\${itaca.nutrition.vision-model:claude-haiku-4-5}") private val model: String,
) : MealAnalyzer {
    override fun fromPhoto(
        image: ByteArray,
        mimeType: String,
    ): MealAnalysis {
        val response =
            chatClient
                .prompt()
                .options(opts())
                .user { it.text(PHOTO_PROMPT).media(MimeType.valueOf(mimeType), ByteArrayResource(image)) }
                .call()
                .entity(MealAnalysisResponse::class.java) ?: return EMPTY
        return response.toAnalysis()
    }

    override fun fromText(description: String): MealAnalysis {
        val response =
            chatClient
                .prompt()
                .options(opts())
                .user { it.text("$TEXT_PROMPT\n\nComida: $description") }
                .call()
                .entity(MealAnalysisResponse::class.java) ?: return EMPTY
        return response.toAnalysis()
    }

    private fun opts() = AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS)

    private fun MealAnalysisResponse.toAnalysis() =
        MealAnalysis(
            description = description.trim(),
            calories = calories,
            macros = macros?.takeIf { it.isNotBlank() },
            mealType = mealType,
            onPlan = onPlan,
        )

    private companion object {
        const val MAX_TOKENS = 512
        val EMPTY = MealAnalysis("", null, null, "lunch", true)
        val FIELDS =
            """
            - description: qué es el plato, en español, breve.
            - calories: estimación de calorías totales (kcal) como entero; null si no puedes estimarlo.
            - macros: gramos estimados de macros con el formato "P 42 · C 46 · G 26" (proteína, carbohidrato,
              grasa); null si no puedes estimarlo.
            - mealType: breakfast, lunch, dinner o snack (tu mejor estimación).
            - onPlan: true si encaja en una dieta paleo antiinflamatoria (sin cereales, legumbres, lácteos,
              azúcar, ultraprocesados ni aceites de semillas); false si no.
            Las calorías y macros son una estimación aproximada, no un dato médico.
            """.trimIndent()
        val PHOTO_PROMPT = "Eres un asistente de nutrición. Mira la foto del plato y responde:\n$FIELDS"
        val TEXT_PROMPT = "Eres un asistente de nutrición. Estima, a partir de la descripción, y responde:\n$FIELDS"
    }
}
