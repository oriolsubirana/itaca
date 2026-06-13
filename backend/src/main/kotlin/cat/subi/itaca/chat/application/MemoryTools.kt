package cat.subi.itaca.chat.application

import cat.subi.itaca.chat.adapter.out.persistence.ChatMemoryEntity
import cat.subi.itaca.chat.adapter.out.persistence.UserMemoryRepository
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MemoryDto(
    val id: Long,
    val content: String,
)

data class MemoryResult(
    val saved: Boolean,
    val memory: MemoryDto? = null,
    val error: String? = null,
)

/**
 * Persistent user memory across chat sessions. Saved facts are injected into
 * the system prompt of every conversation, so "anotado en mi memoria" is only
 * said when it is actually true.
 */
@Service
class MemoryTools(
    private val memories: UserMemoryRepository,
) : ChatTools {
    @Tool(
        name = "save_memory",
        description =
            "Persists a durable personal fact about the user across ALL future conversations (medication and " +
                "doses, conditions, preferences, routines). One concise fact per call. Do NOT use it for " +
                "things that belong to other tools (diary entries, flares, workouts).",
    )
    @Transactional
    fun saveMemory(
        @ToolParam(description = "The fact to remember, short and self-contained") content: String,
    ): MemoryResult {
        if (content.isBlank()) return MemoryResult(saved = false, error = "Empty memory")
        val memory = memories.save(ChatMemoryEntity(content = content.trim()))
        return MemoryResult(saved = true, memory = MemoryDto(memory.id!!, memory.content))
    }

    @Tool(
        name = "forget_memory",
        description =
            "Deletes a saved memory by its id (the ids are listed in the system prompt) " +
                "when the user corrects or retracts it.",
    )
    @Transactional
    fun forgetMemory(
        @ToolParam(description = "Id of the memory to delete") id: Long,
    ): MemoryResult {
        if (!memories.existsById(id)) return MemoryResult(saved = false, error = "Memory $id not found")
        memories.deleteById(id)
        return MemoryResult(saved = true)
    }

    fun allMemories(): List<MemoryDto> = memories.findAllByOrderByIdAsc().map { MemoryDto(it.id!!, it.content) }
}
