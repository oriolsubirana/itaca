package cat.subi.itaca.training.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "strava_account")
class StravaAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "athlete_id")
    var athleteId: Long? = null,
    @Column(name = "access_token")
    var accessToken: String? = null,
    @Column(name = "refresh_token", nullable = false)
    var refreshToken: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "activities")
class ActivityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "strava_id", nullable = false)
    val stravaId: Long,
    @Column(nullable = false)
    var type: String,
    // Raw Strava sport_type (e.g. "Pilates"), kept so the UI can name long-tail activities.
    var sport: String? = null,
    var name: String? = null,
    @Column(name = "start_date", nullable = false)
    var startDate: Instant,
    @Column(name = "distance_m")
    var distanceM: BigDecimal? = null,
    @Column(name = "moving_time_s")
    var movingTimeS: Int? = null,
    @Column(name = "elevation_m")
    var elevationM: BigDecimal? = null,
    @Column(name = "avg_hr")
    var avgHr: BigDecimal? = null,
    @Column(name = "avg_speed_ms")
    var avgSpeedMs: BigDecimal? = null,
    // Fetched lazily from the Strava activity detail endpoint (not in the list response).
    var calories: Int? = null,
    // True once the detail call ran, so genuinely calorie-less activities are not re-fetched.
    @Column(name = "calories_fetched", nullable = false)
    var caloriesFetched: Boolean = false,
)

interface StravaAccountRepository : JpaRepository<StravaAccountEntity, Long> {
    fun findTop1ByOrderByIdDesc(): StravaAccountEntity?
}

interface ActivityRepository : JpaRepository<ActivityEntity, Long> {
    fun findByStravaId(stravaId: Long): ActivityEntity?
}
