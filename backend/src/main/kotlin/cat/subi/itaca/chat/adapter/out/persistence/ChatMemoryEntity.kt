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
@Table(name = "chat_memories")
class ChatMemoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    var content: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

interface UserMemoryRepository : JpaRepository<ChatMemoryEntity, Long> {
    fun findAllByOrderByIdAsc(): List<ChatMemoryEntity>
}
