// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.chat.adapter.`in`.rest

import cat.subi.itaca.chat.application.ChatService
import cat.subi.itaca.chat.application.MessageDto
import cat.subi.itaca.chat.application.SessionDto
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

data class CreateSessionRequest(
    val mode: String = "general",
)

data class SendMessageRequest(
    @field:NotBlank
    val content: String,
)

data class ChunkPayload(
    val text: String,
)

@RestController
@RequestMapping("/api/chat/sessions")
class ChatController(
    private val chatService: ChatService,
) {
    private val log = LoggerFactory.getLogger(ChatController::class.java)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSession(
        @RequestBody request: CreateSessionRequest,
    ): SessionDto = chatService.createSession(request.mode)

    @GetMapping("/{sessionId}/messages")
    fun history(
        @PathVariable sessionId: Long,
    ): List<MessageDto> = chatService.history(sessionId)

    @PostMapping("/{sessionId}/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sendMessage(
        @PathVariable sessionId: Long,
        @RequestBody request: SendMessageRequest,
    ): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        chatService
            .streamReply(sessionId, request.content)
            .subscribe(
                { chunk -> emitter.send(SseEmitter.event().name("chunk").data(ChunkPayload(chunk))) },
                { error ->
                    log.error("Chat stream failed for session {}", sessionId, error)
                    val payload = ChunkPayload("El chat ha fallado, vuelve a intentarlo.")
                    runCatching { emitter.send(SseEmitter.event().name("error").data(payload)) }
                    emitter.complete()
                },
                {
                    runCatching { emitter.send(SseEmitter.event().name("done").data("")) }
                    emitter.complete()
                },
            )
        return emitter
    }

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    companion object {
        /** A reply with several tool roundtrips can take a while. */
        private const val STREAM_TIMEOUT_MS = 120_000L
    }
}
