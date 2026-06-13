package cat.subi.itaca.health.domain

/**
 * Flare severity. Persisted in English (DB check constraint); the UI and the
 * chat present it in Spanish.
 */
enum class FlareSeverity {
    MILD,
    MODERATE,
    SEVERE,
    ;

    val dbValue: String get() = name.lowercase()

    companion object {
        fun from(value: String): FlareSeverity {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.dbValue == normalized }
                ?: throw IllegalArgumentException("Unknown severity: $value (use mild, moderate or severe)")
        }
    }
}
