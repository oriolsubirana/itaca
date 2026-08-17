package cat.subi.itaca.google.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/** One inbox thread awaiting the user's reply (the heuristic stand-in for Gmail's Nudge). */
data class InboxThread(
    val threadId: String,
    val subject: String,
    val from: String,
    val receivedIso: String,
    val ageDays: Int,
)

/** Port: reads Gmail for threads the user still owes a reply to. */
interface GmailReader {
    fun awaitingReply(
        accessToken: String,
        withinDays: Int,
        minAgeDays: Int,
        max: Int,
    ): List<InboxThread>
}

/**
 * Exposes "emails awaiting a reply" to the chat (read-only) and to the REST adapter for the Home
 * agenda glance. Needs a stored Google token (the user signed in with offline access). Read-only;
 * it never sends, archives or modifies mail.
 */
@Service
class GoogleGmailService(
    private val tokens: GoogleTokens,
    private val gmail: GmailReader,
) : ChatTools {
    @Tool(
        name = "query_inbox",
        description =
            "Lists the Gmail threads Oriol still owes a reply to (last message is from someone else, a " +
                "few days old). Read-only. Use it to remind him of pending emails or fold them into a " +
                "daily summary. It can't read full bodies — only sender, subject and age.",
    )
    fun queryInbox(
        @ToolParam(description = "How far back to scan, in days (default 21)", required = false) days: Int?,
    ): String {
        if (tokens.accessToken() == null) return "El correo de Google no está conectado."
        return summarizeInbox(awaitingReply(days))
    }

    fun awaitingReply(days: Int? = null): List<InboxThread> {
        val token = tokens.accessToken() ?: return emptyList()
        val within = (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        return gmail.awaitingReply(token, withinDays = within, minAgeDays = MIN_AGE_DAYS, max = MAX_THREADS)
    }

    private companion object {
        const val DEFAULT_DAYS = 21
        const val MAX_DAYS = 60
        const val MIN_AGE_DAYS = 2
        const val MAX_THREADS = 8
    }
}

/** Pure formatting (testable without Gmail): a compact list the chat can rephrase in Spanish. */
fun summarizeInbox(threads: List<InboxThread>): String {
    if (threads.isEmpty()) return "No tienes correos pendientes de responder."
    return threads.joinToString("\n") { "- ${it.from}: ${it.subject} (hace ${it.ageDays} días)" }
}
