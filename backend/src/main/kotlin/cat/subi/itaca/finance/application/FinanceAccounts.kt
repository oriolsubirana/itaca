package cat.subi.itaca.finance.application

/** The special account names the import/read code keys on (single source of truth). */
object FinanceAccounts {
    const val NEON = "Neon"
    const val NEON_SAVES = "Neon Saves"
    const val FINPENSION = "finpension"

    /** Accounts that have a statement/report parser; the rest use a manual balance. */
    val IMPORTABLE = setOf(NEON, FINPENSION)
}
