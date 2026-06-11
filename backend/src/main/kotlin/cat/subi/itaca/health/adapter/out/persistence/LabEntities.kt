package cat.subi.itaca.health.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "lab_reports")
class LabReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    var date: LocalDate,
    var laboratory: String? = null,
    @Column(name = "storage_path")
    val storagePath: String? = null,
    @Column(nullable = false)
    var status: String = "pending_review",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "lab_results")
class LabResultEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "lab_report_id", nullable = false)
    val labReportId: Long,
    @Column(name = "analyte_id")
    var analyteId: Long? = null,
    @Column(name = "raw_name", nullable = false)
    val rawName: String,
    @Column(nullable = false)
    var value: BigDecimal,
    var unit: String? = null,
    @Column(name = "ref_min")
    var refMin: BigDecimal? = null,
    @Column(name = "ref_max")
    var refMax: BigDecimal? = null,
)

interface LabReportRepository : JpaRepository<LabReportEntity, Long> {
    fun findTop20ByOrderByCreatedAtDesc(): List<LabReportEntity>
}

interface LabResultRepository : JpaRepository<LabResultEntity, Long> {
    fun findByLabReportIdOrderById(labReportId: Long): List<LabResultEntity>

    fun deleteByLabReportId(labReportId: Long)
}
