package cat.subi.itaca.tasks.domain

/**
 * Where a task came from. [wire] is the lowercase form persisted in `tasks.source`
 * (matched by the table's CHECK constraint). [from] is lenient — a null, blank or
 * unknown tag falls back to MANUAL so tagging never blocks creating a task.
 */
enum class TaskSource(
    private val synonyms: Set<String>,
) {
    MANUAL(emptySet()),
    CHAT(emptySet()),
    EMAIL(setOf("gmail", "correo", "mail")),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String?): TaskSource {
            val v = value?.trim()?.lowercase().orEmpty()
            if (v.isEmpty()) return MANUAL
            return entries.firstOrNull { it.wire == v || v in it.synonyms } ?: MANUAL
        }
    }
}
