package cat.subi.itaca.tasks.domain

/** A non-blank task title, trimmed. Built via [of] so the invariant always holds. */
@JvmInline
value class TaskTitle private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): TaskTitle {
            val trimmed = raw.trim()
            require(trimmed.isNotBlank()) { "Task title cannot be blank" }
            return TaskTitle(trimmed)
        }
    }
}
