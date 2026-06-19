package cat.subi.itaca.nutrition.domain

/**
 * When in the day a meal was eaten. [wire] is the lowercase form persisted in
 * `meals.meal_type` (matched by the table's CHECK constraint); [from] is lenient so
 * the chat can pass either the English code or the everyday Spanish word.
 */
enum class MealType(
    private val synonyms: Set<String>,
) {
    BREAKFAST(setOf("desayuno")),
    LUNCH(setOf("comida", "almuerzo")),
    DINNER(setOf("cena")),
    SNACK(setOf("merienda", "tentempie", "tentempié", "picoteo")),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String): MealType {
            val v = value.trim().lowercase()
            return entries.firstOrNull { it.wire == v || v in it.synonyms }
                ?: throw IllegalArgumentException("Unknown meal type: $value")
        }
    }
}
