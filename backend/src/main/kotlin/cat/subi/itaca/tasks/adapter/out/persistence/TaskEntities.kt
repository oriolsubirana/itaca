package cat.subi.itaca.tasks.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "tasks")
class TaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    var title: String,
    var notes: String? = null,
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
    @Column(nullable = false)
    var done: Boolean = false,
    @Column(name = "done_at")
    var doneAt: Instant? = null,
    @Column(nullable = false)
    val source: String = "manual",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

interface TaskRepository : JpaRepository<TaskEntity, Long> {
    fun findByDoneOrderByCreatedAtDesc(done: Boolean): List<TaskEntity>
}
