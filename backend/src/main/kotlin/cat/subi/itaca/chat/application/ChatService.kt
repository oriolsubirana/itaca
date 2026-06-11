package cat.subi.itaca.chat.application

import cat.subi.itaca.chat.adapter.out.persistence.ChatMessageEntity
import cat.subi.itaca.chat.adapter.out.persistence.ChatMessageRepository
import cat.subi.itaca.chat.adapter.out.persistence.ChatSessionEntity
import cat.subi.itaca.chat.adapter.out.persistence.ChatSessionRepository
import cat.subi.itaca.chat.domain.MessageRole
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

data class SessionDto(
    val id: Long,
    val mode: String,
    val createdAt: String,
)

data class MessageDto(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: String,
)

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val sessions: ChatSessionRepository,
    private val messages: ChatMessageRepository,
    private val tools: List<ChatTools>,
) {
    fun createSession(mode: String): SessionDto {
        require(mode in VALID_MODES) { "Invalid mode: $mode" }
        val session = sessions.save(ChatSessionEntity(mode = mode))
        return session.toDto()
    }

    fun history(sessionId: Long): List<MessageDto> =
        messages
            .findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)
            .filter { it.role != MessageRole.TOOL.name }
            .map { MessageDto(it.id!!, it.role, it.content, it.createdAt.toString()) }

    /**
     * Persists the user message, streams Claude's reply (executing tools as
     * needed) and persists the full assistant message on completion. If the
     * stream fails before producing anything, the user message is removed so
     * a failed send leaves no trace; if it fails midway, the partial reply is
     * kept alongside the user message.
     */
    fun streamReply(
        sessionId: Long,
        userText: String,
    ): Flux<String> {
        val session =
            sessions
                .findById(sessionId)
                .orElseThrow { NoSuchElementException("Session $sessionId not found") }
        val userMessage =
            messages.save(ChatMessageEntity(sessionId = sessionId, role = MessageRole.USER.name, content = userText))

        val history = promptMessages(sessionId)
        val reply = StringBuilder()

        @Suppress("SpreadOperator") // ChatClient only offers a vararg overload for tool objects
        return chatClient
            .prompt()
            .system(SystemPrompts.forMode(session.mode))
            .messages(history)
            .tools(*tools.toTypedArray())
            .stream()
            .content()
            .doOnNext { reply.append(it) }
            .doOnComplete { persistReply(sessionId, reply) }
            .doOnError {
                if (reply.isEmpty()) {
                    messages.delete(userMessage)
                } else {
                    persistReply(sessionId, reply)
                }
            }
    }

    private fun persistReply(
        sessionId: Long,
        reply: StringBuilder,
    ) {
        if (reply.isNotEmpty()) {
            messages.save(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT.name,
                    content = reply.toString(),
                ),
            )
        }
    }

    private fun promptMessages(sessionId: Long): List<Message> =
        messages
            .findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)
            .takeLast(HISTORY_WINDOW)
            .mapNotNull {
                when (it.role) {
                    MessageRole.USER.name -> UserMessage(it.content)
                    MessageRole.ASSISTANT.name -> AssistantMessage(it.content)
                    else -> null
                }
            }

    private fun ChatSessionEntity.toDto() = SessionDto(id!!, mode, createdAt.toString())

    companion object {
        private val VALID_MODES = setOf("general", "workout")

        /** A gym session lasts ~1h with many short turns; keep a generous window. */
        private const val HISTORY_WINDOW = 60
    }
}
