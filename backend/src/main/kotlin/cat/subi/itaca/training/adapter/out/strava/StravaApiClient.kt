package cat.subi.itaca.training.adapter.out.strava

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant

data class StravaToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val athleteId: Long?,
)

/** One activity as imported from Strava (type already normalized to bike/run/hike/other). */
data class StravaActivity(
    val stravaId: Long,
    val type: String,
    val name: String?,
    val startDate: Instant,
    val distanceM: BigDecimal?,
    val movingTimeS: Int?,
    val elevationM: BigDecimal?,
    val avgHr: BigDecimal?,
    val avgSpeedMs: BigDecimal?,
)

/**
 * Talks to Strava's OAuth and Activities API. Garmin auto-syncs activities to
 * Strava, so this is the bridge. Base URLs are injectable so tests can stub them.
 */
@Component
class StravaApiClient(
    @Value("\${itaca.strava.client-id:}") private val clientId: String,
    @Value("\${itaca.strava.client-secret:}") private val clientSecret: String,
    @Value("\${itaca.strava.redirect-uri:}") private val redirectUri: String,
    @Value("\${itaca.strava.auth-base:https://www.strava.com}") authBase: String,
    @Value("\${itaca.strava.api-base:https://www.strava.com/api/v3}") apiBase: String,
) {
    private val auth = RestClient.create(authBase)
    private val api = RestClient.create(apiBase)

    fun authorizeUrl(): String =
        "$authBaseUrl/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri" +
            "&response_type=code&approval_prompt=auto&scope=activity:read_all"

    fun exchangeCode(code: String): StravaToken =
        token(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
            },
        )

    fun refresh(refreshToken: String): StravaToken =
        token(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
            },
        )

    fun activities(
        accessToken: String,
        perPage: Int,
    ): List<StravaActivity> {
        val rows =
            api
                .get()
                .uri("/athlete/activities?per_page={n}", perPage)
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(LIST_OF_MAPS) ?: emptyList()
        return rows.map { row ->
            StravaActivity(
                stravaId = (row["id"] as Number).toLong(),
                type = mapType(row["sport_type"] as? String ?: row["type"] as? String),
                name = row["name"] as? String,
                startDate = Instant.parse(row["start_date"] as String),
                distanceM = num(row["distance"]),
                movingTimeS = (row["moving_time"] as? Number)?.toInt(),
                elevationM = num(row["total_elevation_gain"]),
                avgHr = num(row["average_heartrate"]),
                avgSpeedMs = num(row["average_speed"]),
            )
        }
    }

    private fun token(form: LinkedMultiValueMap<String, String>): StravaToken {
        form.add("client_id", clientId)
        form.add("client_secret", clientSecret)
        val m =
            auth
                .post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP) ?: error("Empty Strava token response")

        @Suppress("UNCHECKED_CAST")
        val athlete = m["athlete"] as? Map<String, Any?>
        return StravaToken(
            accessToken = m["access_token"] as String,
            refreshToken = m["refresh_token"] as String,
            expiresAt = Instant.ofEpochSecond((m["expires_at"] as Number).toLong()),
            athleteId = (athlete?.get("id") as? Number)?.toLong(),
        )
    }

    private val authBaseUrl = authBase.trimEnd('/')

    private fun num(v: Any?): BigDecimal? = (v as? Number)?.let { BigDecimal.valueOf(it.toDouble()) }

    private fun mapType(sport: String?): String =
        when (sport?.lowercase()) {
            "ride", "virtualride", "mountainbikeride", "gravelride", "ebikeride" -> "bike"
            "run", "trailrun", "virtualrun" -> "run"
            "hike" -> "hike"
            "weighttraining", "workout", "crossfit", "highintensityintervaltraining" -> "gym"
            else -> "other"
        }

    private companion object {
        val MAP = object : ParameterizedTypeReference<Map<String, Any?>>() {}
        val LIST_OF_MAPS = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }
}
