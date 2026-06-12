package cat.subi.itaca.training.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "sets")
class SetEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "workout_id", nullable = false)
    val workoutId: Long,
    @Column(name = "exercise_id", nullable = false)
    val exerciseId: Long,
    @Column(name = "weight_kg", nullable = false)
    val weightKg: BigDecimal,
    @Column(nullable = false)
    val reps: Int,
    @Column(nullable = false)
    val position: Int,
    val rpe: BigDecimal? = null,
)
