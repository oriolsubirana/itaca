package cat.subi.itaca.health.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "medical_documents")
class MedicalDocumentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "doc_date")
    var docDate: LocalDate? = null,
    var type: String? = null,
    var provider: String? = null,
    var center: String? = null,
    var filename: String? = null,
    @Column(name = "storage_path")
    val storagePath: String? = null,
    @Column(name = "full_text")
    var fullText: String? = null,
    @Column(nullable = false)
    var status: String = "pending_review",
    @Column(nullable = false)
    var extracting: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "medical_diagnoses")
class MedicalDiagnosisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "document_id", nullable = false)
    val documentId: Long,
    var code: String? = null,
    @Column(nullable = false)
    var label: String,
    @Column(name = "diagnosed_on")
    var diagnosedOn: LocalDate? = null,
)

@Entity
@Table(name = "medical_medications")
class MedicalMedicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "document_id", nullable = false)
    val documentId: Long,
    @Column(nullable = false)
    var name: String,
    var dose: String? = null,
    var schedule: String? = null,
    var duration: String? = null,
    var reason: String? = null,
)

interface MedicalDocumentRepository : JpaRepository<MedicalDocumentEntity, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<MedicalDocumentEntity>

    /** Row lock to serialize concurrent extraction jobs for the same document. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MedicalDocumentEntity d WHERE d.id = :id")
    fun lockById(id: Long): MedicalDocumentEntity?
}

interface MedicalDiagnosisRepository : JpaRepository<MedicalDiagnosisEntity, Long> {
    fun findByDocumentIdOrderById(documentId: Long): List<MedicalDiagnosisEntity>

    @Modifying
    @Query("DELETE FROM MedicalDiagnosisEntity d WHERE d.documentId = :documentId")
    fun deleteByDocumentId(documentId: Long)
}

interface MedicalMedicationRepository : JpaRepository<MedicalMedicationEntity, Long> {
    fun findByDocumentIdOrderById(documentId: Long): List<MedicalMedicationEntity>

    @Modifying
    @Query("DELETE FROM MedicalMedicationEntity m WHERE m.documentId = :documentId")
    fun deleteByDocumentId(documentId: Long)
}
