package cat.subi.itaca.finance.application

/** Port: maps transaction descriptions to canonical category codes via the model (the long-tail tier). */
fun interface FinanceCategorizerAi {
    fun categorize(descriptions: List<String>): Map<String, String>
}
