package cat.subi.itaca.nutrition.adapter.out.persistence

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
@Table(name = "meals")
class MealEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val date: LocalDate,
    @Column(name = "meal_type", nullable = false)
    var mealType: String,
    @Column(nullable = false)
    var description: String,
    @Column(name = "on_plan")
    var onPlan: Boolean? = null,
    var notes: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

interface MealRepository : JpaRepository<MealEntity, Long> {
    fun findByDateGreaterThanEqualOrderByDateDescIdDesc(from: LocalDate): List<MealEntity>
}
