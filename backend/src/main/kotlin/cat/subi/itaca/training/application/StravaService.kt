package cat.subi.itaca.training.application

import cat.subi.itaca.training.adapter.out.persistence.ActivityEntity
import cat.subi.itaca.training.adapter.out.persistence.ActivityRepository
import cat.subi.itaca.training.adapter.out.persistence.StravaAccountEntity
import cat.subi.itaca.training.adapter.out.persistence.StravaAccountRepository
import cat.subi.itaca.training.adapter.out.strava.StravaActivity
import cat.subi.itaca.training.adapter.out.strava.StravaApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class SyncResult(
    val imported: Int,
    val total: Int,
)

/**
 * Strava connection (single account) and activity sync. Garmin pushes activities
 * to Strava automatically; this pulls them in (bike/run/hike).
 */
@Service
class StravaService(
    private val client: StravaApiClient,
    private val accounts: StravaAccountRepository,
    private val activities: ActivityRepository,
    @Value("\${itaca.strava.app-url:}") val appUrl: String,
    @Value("\${itaca.strava.refresh-token:}") private val seedRefreshToken: String,
) {
    private val log = LoggerFactory.getLogger(StravaService::class.java)

    fun authorizeUrl(): String = client.authorizeUrl()

    fun isConnected(): Boolean = accounts.count() > 0 || seedRefreshToken.isNotBlank()

    /** Exchanges the OAuth code for tokens and stores the (single) account. */
    @Transactional
    fun connect(code: String) {
        val token = client.exchangeCode(code)
        accounts.deleteAll()
        accounts.save(
            StravaAccountEntity(
                athleteId = token.athleteId,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAt = token.expiresAt,
            ),
        )
        log.info("Strava connected for athlete {}", token.athleteId)
    }

    /** Pulls the latest activities and upserts them by Strava id. */
    @Transactional
    fun sync(): SyncResult {
        val token = validAccessToken()
        val fetched = client.activities(token, ACTIVITIES_PER_SYNC)
        var imported = 0
        fetched.forEach { a ->
            val existing = activities.findByStravaId(a.stravaId)
            if (existing == null) {
                activities.save(toEntity(a))
                imported++
            } else {
                update(existing, a)
                activities.save(existing)
            }
        }
        log.info("Strava sync: {} fetched, {} new", fetched.size, imported)
        return SyncResult(imported, activities.count().toInt())
    }

    private fun validAccessToken(): String {
        val account = accounts.findTop1ByOrderByIdDesc() ?: seedAccount()
        if (account.accessToken == null || account.expiresAt.isBefore(Instant.now().plusSeconds(REFRESH_MARGIN_S))) {
            val token = client.refresh(account.refreshToken)
            account.accessToken = token.accessToken
            account.refreshToken = token.refreshToken
            account.expiresAt = token.expiresAt
            token.athleteId?.let { account.athleteId = it }
            accounts.save(account)
        }
        return account.accessToken!!
    }

    /**
     * Bootstraps the single account from the configured refresh token (env) when
     * none was created via the web connect flow. Expiry is in the past so the
     * caller refreshes immediately. Single-user prod can skip OAuth entirely:
     * obtain a refresh token once by hand and set itaca.strava.refresh-token.
     */
    private fun seedAccount(): StravaAccountEntity {
        if (seedRefreshToken.isBlank()) error("Strava is not connected")
        log.info("Bootstrapping Strava account from configured refresh token")
        return accounts.save(StravaAccountEntity(refreshToken = seedRefreshToken, expiresAt = Instant.EPOCH))
    }

    private fun toEntity(a: StravaActivity) =
        ActivityEntity(
            stravaId = a.stravaId,
            type = a.type,
            sport = a.sport,
            name = a.name,
            startDate = a.startDate,
            distanceM = a.distanceM,
            movingTimeS = a.movingTimeS,
            elevationM = a.elevationM,
            avgHr = a.avgHr,
            avgSpeedMs = a.avgSpeedMs,
        )

    private fun update(
        e: ActivityEntity,
        a: StravaActivity,
    ) {
        e.type = a.type
        e.sport = a.sport
        e.name = a.name
        e.startDate = a.startDate
        e.distanceM = a.distanceM
        e.movingTimeS = a.movingTimeS
        e.elevationM = a.elevationM
        e.avgHr = a.avgHr
        e.avgSpeedMs = a.avgSpeedMs
    }

    private companion object {
        const val ACTIVITIES_PER_SYNC = 50
        const val REFRESH_MARGIN_S = 60L
    }
}
