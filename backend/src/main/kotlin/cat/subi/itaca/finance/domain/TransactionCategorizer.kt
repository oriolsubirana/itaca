package cat.subi.itaca.finance.domain

/**
 * Hybrid categorization into canonical codes: a savings-Space movement is a
 * transfer; otherwise the bank's own category is mapped (tier 1); unknown or
 * "uncategorized" rows fall back to keyword rules on the description (tier 2),
 * then to "other". A Claude semantic tier can be added later for the long tail.
 */
class TransactionCategorizer {
    fun categorize(
        bankCategory: String,
        transfer: Boolean,
        description: String,
    ): String {
        if (transfer) return "transfers"
        BANK_CATEGORIES[bankCategory.lowercase()]?.let { return it }
        return keywordRule(description) ?: "other"
    }

    private fun keywordRule(description: String): String? {
        val d = description.lowercase()
        return KEYWORDS.firstOrNull { (_, words) -> words.any { d.contains(it) } }?.first
    }

    private companion object {
        // neon's category vocabulary -> canonical codes.
        val BANK_CATEGORIES =
            mapOf(
                "income" to "income",
                "income_salary" to "income",
                "housing" to "housing",
                "transport" to "transport",
                "travel" to "travel",
                "food" to "restaurants",
                "household" to "groceries",
                "shopping" to "shopping",
                "health" to "health",
                "cash" to "cash",
                "leisure" to "leisure",
                "invest" to "investment",
                "finances" to "fees",
                "work" to "work",
            )

        // Tier 2: cheap keyword rules for the "uncategorized" tail.
        val KEYWORDS =
            listOf(
                "groceries" to listOf("lidl", "coop", "migros", "aldi", "supermarkt"),
                "restaurants" to listOf("restaurant", "cafe", "café", "glovo", "pizz", "bar ", "sugar papi"),
                "transport" to listOf("uber", "cabify", "sbb", "vueling", "airlines", "parking", "avia", "post ch"),
                "shopping" to listOf("amazon", "zalando", "zara", "apple", "alipay"),
            )
    }
}
