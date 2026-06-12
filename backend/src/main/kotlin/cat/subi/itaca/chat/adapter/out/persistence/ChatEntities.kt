package cat.subi.itaca.chat.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "chat_sessions")
class ChatSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    var title: String? = null,
    @Column(nullable = false)
    val mode: String = "general",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "chat_messages")
class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "session_id", nullable = false)
    val sessionId: Long,
    @Column(nullable = false)
    val role: String,
    @Column(nullable = false)
    val content: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

interface ChatSessionRepository : JpaRepository<ChatSessionEntity, Long>

interface ChatMessageRepository : JpaRepository<ChatMessageEntity, Long> {
    fun findBySessionIdOrderByCreatedAtAscIdAsc(sessionId: Long): List<ChatMessageEntity>
}
