package cat.subi.itaca.training.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "workouts")
class WorkoutEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val date: LocalDate,
    @Column(name = "routine_id", nullable = false)
    val routineId: Long,
    var notes: String? = null,
    @Column(nullable = false)
    var completed: Boolean = false,
)
