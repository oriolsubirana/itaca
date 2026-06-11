package cat.subi.itaca.health.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

@Entity
@Table(name = "diary_entries")
class DiaryEntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val date: LocalDate,
    var bristol: Int? = null,
    var pain: Int? = null,
    var urgency: Int? = null,
    @Column(nullable = false)
    var blood: Boolean = false,
    @Column(name = "bowel_movements")
    var bowelMovements: Int? = null,
    var stress: Int? = null,
    var notes: String? = null,
)

@Entity
@Table(name = "flares")
class FlareEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    @Column(nullable = false)
    val severity: String,
    var notes: String? = null,
)

interface DiaryEntryRepository : JpaRepository<DiaryEntryEntity, Long> {
    fun findByDate(date: LocalDate): DiaryEntryEntity?

    fun findByDateGreaterThanEqualOrderByDateDesc(from: LocalDate): List<DiaryEntryEntity>
}

interface FlareRepository : JpaRepository<FlareEntity, Long> {
    fun findFirstByEndDateIsNullOrderByStartDateDesc(): FlareEntity?

    fun findTop10ByOrderByStartDateDesc(): List<FlareEntity>
}
