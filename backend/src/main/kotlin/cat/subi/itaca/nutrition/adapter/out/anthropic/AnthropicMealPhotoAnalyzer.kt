package cat.subi.itaca.nutrition.adapter.out.anthropic

import cat.subi.itaca.nutrition.application.MealAnalysis
import cat.subi.itaca.nutrition.application.MealPhotoAnalyzer
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/** Structured-output target for the vision call (var + defaults so any Jackson can bind it). */
class MealPhotoResponse {
    var description: String = ""
    var calories: Int? = null
    var mealType: String = "lunch"
    var onPlan: Boolean = true
}

/**
 * Reads a meal photo with Claude vision and proposes the meal's description, estimated calories,
 * type and anti-inflammatory paleo adherence. The estimate is approximate (not a medical figure)
 * and the user reviews it before it is saved.
 */
@Component
class AnthropicMealPhotoAnalyzer(
    private val chatClient: ChatClient,
    @Value("\${itaca.nutrition.vision-model:claude-haiku-4-5}") private val model: String,
) : MealPhotoAnalyzer {
    override fun analyze(
        image: ByteArray,
        mimeType: String,
    ): MealAnalysis {
        val response =
            chatClient
                .prompt()
                .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
                .user { it.text(PROMPT).media(MimeType.valueOf(mimeType), ByteArrayResource(image)) }
                .call()
                .entity(MealPhotoResponse::class.java) ?: return MealAnalysis("", null, "lunch", true)
        return MealAnalysis(
            description = response.description.trim(),
            calories = response.calories,
            mealType = response.mealType,
            onPlan = response.onPlan,
        )
    }

    private companion object {
        const val MAX_TOKENS = 512
        val PROMPT =
            """
            Eres un asistente de nutrición. Mira la foto de un plato de comida y responde:
            - description: qué hay en el plato, en español, breve (ej.: "salmón a la plancha con brócoli y boniato").
            - calories: estimación de calorías totales del plato (kcal) como entero; null si no puedes estimarlo.
            - mealType: breakfast, lunch, dinner o snack (tu mejor estimación por el tipo de comida).
            - onPlan: true si encaja en una dieta paleo antiinflamatoria (sin cereales, legumbres, lácteos,
              azúcar, ultraprocesados ni aceites de semillas); false si no.
            Las calorías son una estimación aproximada, no un dato médico.
            """.trimIndent()
    }
}
