package cat.subi.itaca.shared.chat

/**
 * Marker for application services that expose @Tool methods to the chat.
 * The chat module collects all ChatTools beans and registers them with the
 * ChatClient, so bounded contexts never depend on each other directly.
 */
interface ChatTools
