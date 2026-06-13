package cat.subi.itaca.shared.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Shared ChatClient over the autoconfigured Anthropic model. Used by the chat
 * module (conversation) and by health (lab report extraction).
 */
@Configuration
class AiClientConfig {
    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient = builder.build()
}
