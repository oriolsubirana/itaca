package cat.subi.itaca.health.adapter.out.anthropic

import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** One name -> canonical code decision from the model (code null = no dictionary match). */
class CanonicalMapping {
    var name: String = ""
    var code: String? = null
}

class MappingResponse {
    var mappings: List<CanonicalMapping> = emptyList()
}

/** A name to normalize, with its printed unit (helps the model tell relative from absolute). */
data class NameToMap(
    val name: String,
    val unit: String?,
)

/**
 * Semantic normalization: asks the model to map free-text lab result names to the
 * dictionary's canonical codes across languages (es/ca/en/de/fr). This replaces the
 * endless per-synonym maintenance for the multilingual long tail. It only maps to
 * existing codes; the unit guard and the review gate remain backstops downstream.
 */
@Component
class SemanticAnalyteMatcher(
    private val chatClient: ChatClient,
    private val jdbc: JdbcTemplate,
    @Value("\${itaca.normalization.model:claude-haiku-4-5}") private val model: String,
) {
    /** Returns name(lowercased,trimmed) -> code for the inputs the model confidently matched. */
    fun mapToCodes(names: List<NameToMap>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        val dictionary = loadDictionary()
        val validCodes = dictionary.map { it.code }.toSet()
        val response =
            chatClient
                .prompt()
                .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
                .user { it.text(buildPrompt(dictionary, names)) }
                .call()
                .entity(MappingResponse::class.java) ?: return emptyMap()
        return response.mappings
            .filter { it.name.isNotBlank() && it.code != null && it.code in validCodes }
            .associate { it.name.trim().lowercase() to it.code!! }
    }

    private data class DictEntry(
        val code: String,
        val name: String,
        val unit: String,
    )

    private fun loadDictionary(): List<DictEntry> =
        jdbc.query("SELECT code, name, canonical_unit FROM analytes ORDER BY code") { rs, _ ->
            DictEntry(rs.getString("code"), rs.getString("name"), rs.getString("canonical_unit"))
        }

    private fun buildPrompt(
        dictionary: List<DictEntry>,
        names: List<NameToMap>,
    ): String {
        val dict = dictionary.joinToString("\n") { "${it.code} | ${it.name} | ${it.unit}" }
        val inputs = names.joinToString("\n") { "${it.name} | ${it.unit ?: "?"}" }
        return """
            You normalize laboratory result names to a fixed dictionary, across languages
            (Spanish, Catalan, English, German, French). For each input name, return the
            dictionary CODE that denotes the SAME measurement, or null if none fits.
            Rules:
            - Match by meaning across languages (e.g. "Volumen"/"Volume"/"Volum" are the same).
            - Do NOT map when it is a different measurement: a percentage or relative value is
              not the absolute count; a corpuscular volume is not a count; a different specimen
              (urine vs blood) is different. Use the unit to decide.
            - When unsure, return null. NEVER invent a code outside the dictionary.

            Dictionary (code | canonical name | unit):
            $dict

            Names to map (name | unit):
            $inputs

            Return one {name, code} per input name, code null if no match.
            """.trimIndent()
    }

    companion object {
        private const val MAX_TOKENS = 8192
    }
}
