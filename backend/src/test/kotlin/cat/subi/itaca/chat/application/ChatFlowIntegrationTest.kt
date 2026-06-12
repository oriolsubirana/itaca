package cat.subi.itaca.chat.application

import cat.subi.itaca.TestcontainersConfiguration
import cat.subi.itaca.chat.domain.MessageRole
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Orchestration test with a stubbed model: verifies session creation, history
 * and that user + assistant messages are persisted around the stream.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class ChatFlowIntegrationTest {
    @Autowired
    lateinit var chatService: ChatService

    @MockitoBean
    lateinit var chatModel: ChatModel

    private fun chunk(text: String) = ChatResponse(listOf(Generation(AssistantMessage(text))))

    @Test
    fun `streams the reply and persists both sides of the conversation`() {
        given(chatModel.options).willReturn(ChatOptions.builder().build())
        given(chatModel.defaultOptions).willReturn(ChatOptions.builder().build())
        given(chatModel.stream(any(Prompt::class.java)))
            .willReturn(Flux.just(chunk("Apuntado: "), chunk("jalón 45 kg × 12.")))

        val session = chatService.createSession("workout")
        val streamed =
            chatService
                .streamReply(session.id, "jalón 45 por 12")
                .collectList()
                .block()!!
                .joinToString("")

        assertEquals("Apuntado: jalón 45 kg × 12.", streamed)

        val history = chatService.history(session.id)
        assertEquals(2, history.size)
        assertEquals(MessageRole.USER.name, history[0].role)
        assertEquals("jalón 45 por 12", history[0].content)
        assertEquals(MessageRole.ASSISTANT.name, history[1].role)
        assertEquals("Apuntado: jalón 45 kg × 12.", history[1].content)
    }

    @Test
    fun `rejects invalid session modes`() {
        val error = runCatching { chatService.createSession("yolo") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `does not leave an orphaned user message when the stream fails before any chunk`() {
        given(chatModel.options).willReturn(ChatOptions.builder().build())
        given(chatModel.defaultOptions).willReturn(ChatOptions.builder().build())
        given(chatModel.stream(any(Prompt::class.java)))
            .willReturn(Flux.error(IllegalStateException("anthropic down")))

        val session = chatService.createSession("general")
        val outcome =
            runCatching {
                chatService.streamReply(session.id, "hola").collectList().block()
            }

        assertTrue(outcome.isFailure)
        assertTrue(chatService.history(session.id).isEmpty(), "failed sends must leave no trace")
    }

    @Test
    fun `keeps the user message and the partial reply when the stream fails midway`() {
        given(chatModel.options).willReturn(ChatOptions.builder().build())
        given(chatModel.defaultOptions).willReturn(ChatOptions.builder().build())
        given(chatModel.stream(any(Prompt::class.java)))
            .willReturn(Flux.concat(Flux.just(chunk("Apuntado: jal")), Flux.error(IllegalStateException("cut"))))

        val session = chatService.createSession("workout")
        runCatching { chatService.streamReply(session.id, "jalón 45 por 12").collectList().block() }

        val history = chatService.history(session.id)
        assertEquals(2, history.size)
        assertEquals("jalón 45 por 12", history[0].content)
        assertEquals("Apuntado: jal", history[1].content)
    }
}
