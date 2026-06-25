package cat.subi.itaca.google.adapter.out

import cat.subi.itaca.google.application.GmailReader
import cat.subi.itaca.google.application.InboxThread
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Gmail payloads (only the fields we use; Jackson 3 ignores the rest). */
class GmailProfile {
    var emailAddress: String? = null
}

class ThreadsListResponse {
    var threads: List<ThreadRef> = emptyList()
}

class ThreadRef {
    var id: String? = null
}

class ThreadResponse {
    var messages: List<GmailMessage> = emptyList()
}

class GmailMessage {
    var internalDate: String? = null
    var payload: GmailPayload? = null
}

class GmailPayload {
    var headers: List<GmailHeader> = emptyList()
}

class GmailHeader {
    var name: String? = null
    var value: String? = null
}

/**
 * Reads Gmail over REST to surface threads awaiting the user's reply (Gmail's "Nudge" signal is not
 * exposed by the API, so this reproduces it heuristically: a thread whose LAST message is from
 * someone else and is at least N days old). Read-only, nothing is stored. Base URL is injectable so
 * tests can point it at a stub.
 */
@Component
class GoogleGmailClient(
    @Value("\${itaca.google.gmail-base:https://gmail.googleapis.com/gmail/v1}") base: String,
) : GmailReader {
    private val api = RestClient.create(base)
    private val log = LoggerFactory.getLogger(GoogleGmailClient::class.java)

    override fun awaitingReply(
        accessToken: String,
        withinDays: Int,
        minAgeDays: Int,
        max: Int,
    ): List<InboxThread> {
        val me = myEmail(accessToken) ?: return emptyList()
        val ids = threadIds(accessToken, withinDays)
        val now = Instant.now()
        return ids
            .mapNotNull { id -> runCatching { toAwaiting(accessToken, id, me, now, minAgeDays) }.getOrNull() }
            .sortedByDescending { it.ageDays }
            .take(max)
    }

    private fun myEmail(accessToken: String): String? =
        runCatching {
            api
                .get()
                .uri { it.path("/users/me/profile").build() }
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(GmailProfile::class.java)
                ?.emailAddress
                ?.lowercase()
        }.onFailure { log.warn("Gmail profile read failed: {}", it.message) }.getOrNull()

    private fun threadIds(
        accessToken: String,
        withinDays: Int,
    ): List<String> =
        runCatching {
            api
                .get()
                .uri { b ->
                    b
                        .path("/users/me/threads")
                        .queryParam("q", "in:inbox -from:me newer_than:${withinDays}d")
                        .queryParam("maxResults", SCAN_LIMIT)
                        .build()
                }.header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(ThreadsListResponse::class.java)
                ?.threads
                .orEmpty()
                .mapNotNull { it.id }
        }.onFailure { log.warn("Gmail threads list failed: {}", it.message) }.getOrNull().orEmpty()

    /** Inspect one thread's last message; return it only if it's awaiting the user's reply. */
    private fun toAwaiting(
        accessToken: String,
        threadId: String,
        me: String,
        now: Instant,
        minAgeDays: Int,
    ): InboxThread? {
        val thread =
            api
                .get()
                .uri { b ->
                    b
                        .path("/users/me/threads/{id}")
                        .queryParam("format", "metadata")
                        .queryParam("metadataHeaders", "From")
                        .queryParam("metadataHeaders", "Subject")
                        .build(threadId)
                }.header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(ThreadResponse::class.java)
        val last = thread?.messages?.lastOrNull() ?: return null
        val from = header(last, "From") ?: return null
        val received = last.internalDate?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val ageDays = ChronoUnit.DAYS.between(received, now).toInt()
        // Skip if the last word is ours (not waiting on us) or it's too recent to nag about.
        if (from.contains(me, ignoreCase = true) || ageDays < minAgeDays) return null
        return InboxThread(
            threadId = threadId,
            subject = header(last, "Subject")?.takeIf { it.isNotBlank() } ?: "(sin asunto)",
            from = senderName(from),
            receivedIso = received.toString(),
            ageDays = ageDays,
        )
    }

    private fun header(
        message: GmailMessage,
        name: String,
    ): String? =
        message.payload
            ?.headers
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.value

    private companion object {
        const val SCAN_LIMIT = 25
    }
}

/** "Emma García <emma@x.com>" -> "Emma García"; a bare address stays as is. Quotes stripped. */
fun senderName(from: String): String {
    val display = from.substringBefore("<").trim().trim('"')
    return display.ifBlank {
        from
            .substringAfter("<")
            .substringBefore(">")
            .trim()
            .ifBlank { from.trim() }
    }
}
