package cat.subi.itaca.finance.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Runs the AI tier over the transactions still left as "other" and applies the result. */
@Service
class FinanceAiCategorizationService(
    private val jdbc: JdbcTemplate,
    private val ai: FinanceCategorizerAi,
) {
    @Transactional
    fun categorizeOther(): Int {
        val descriptions =
            jdbc.queryForList(
                "SELECT DISTINCT description FROM transactions WHERE category = 'other' LIMIT ?",
                String::class.java,
                MAX_DESCRIPTIONS,
            )
        if (descriptions.isEmpty()) return 0
        var updated = 0
        ai.categorize(descriptions).forEach { (description, category) ->
            if (category != "other") {
                updated +=
                    jdbc.update(
                        "UPDATE transactions SET category = ? WHERE description = ? AND category = 'other'",
                        category,
                        description,
                    )
            }
        }
        return updated
    }

    private companion object {
        const val MAX_DESCRIPTIONS = 100
    }
}
