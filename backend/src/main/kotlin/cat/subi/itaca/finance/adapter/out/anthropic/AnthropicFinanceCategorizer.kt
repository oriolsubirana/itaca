package cat.subi.itaca.finance.adapter.out.anthropic

import cat.subi.itaca.finance.application.FinanceCategorizerAi
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** One description -> canonical category decision from the model (category null = unclear). */
class CategoryMapping {
    var description: String = ""
    var category: String? = null
}

class CategoryMappingResponse {
    var mappings: List<CategoryMapping> = emptyList()
}

/**
 * Tier 3 of the hybrid categorization: asks the model to map the "uncategorized"
 * long tail of merchant descriptions to the fixed category codes. It only maps to
 * existing codes; the user can still correct any row by hand.
 */
@Component
class AnthropicFinanceCategorizer(
    private val chatClient: ChatClient,
    @Value("\${itaca.normalization.model:claude-haiku-4-5}") private val model: String,
) : FinanceCategorizerAi {
    override fun categorize(descriptions: List<String>): Map<String, String> {
        if (descriptions.isEmpty()) return emptyMap()
        val response =
            chatClient
                .prompt()
                .options(AnthropicChatOptions.builder().model(model).maxTokens(MAX_TOKENS))
                .user { it.text(buildPrompt(descriptions)) }
                .call()
                .entity(CategoryMappingResponse::class.java) ?: return emptyMap()
        return response.mappings
            .filter { it.description.isNotBlank() && it.category != null && it.category in CATEGORIES }
            .associate { it.description.trim() to it.category!! }
    }

    private fun buildPrompt(descriptions: List<String>): String =
        """
        You categorize bank-transaction descriptions (merchant names, mostly Swiss/Spanish)
        into a FIXED set of category codes. For each input, return the best-fitting code, or
        null if genuinely unclear. NEVER invent a code outside the list.

        Categories:
        - groceries: supermarkets, food shops (Coop, Migros, Lidl, Mercadona)
        - restaurants: eating out, cafes, bars, food delivery (Glovo, Uber Eats)
        - transport: public transport, taxi/rideshare, flights, parking (SBB, Uber, Vueling)
        - fuel: petrol stations
        - travel: hotels, Airbnb, car rental
        - housing: rent, home internet/phone/electricity (Sunrise, Yallo, EWZ)
        - utilities: other household utilities
        - health: pharmacy, doctor, health insurance (Sympany, CSS)
        - subscriptions: recurring digital services (Spotify, Netflix, OpenAI, GitHub)
        - shopping: retail and online shopping (Amazon, Zara, Apple, Zalando)
        - leisure: entertainment, hobbies, events
        - fees: bank/card fees, credit-card payments, loans (Swisscard, Cembra)
        - cash: ATM withdrawals
        - investment: contributions to investments/pension
        - income: salary, refunds, money received (employers, people paying you)
        - work: work-related
        - other: genuinely does not fit

        Descriptions to categorize (one per line):
        ${descriptions.joinToString("\n")}

        Return one {description, category} per input; category null if unclear.
        """.trimIndent()

    private companion object {
        const val MAX_TOKENS = 8192
        val CATEGORIES =
            setOf(
                "groceries",
                "restaurants",
                "transport",
                "fuel",
                "travel",
                "housing",
                "utilities",
                "health",
                "subscriptions",
                "shopping",
                "leisure",
                "fees",
                "cash",
                "investment",
                "income",
                "work",
                "other",
            )
    }
}
