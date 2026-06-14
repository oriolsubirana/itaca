package cat.subi.itaca.training.application

import cat.subi.itaca.TestcontainersConfiguration
import cat.subi.itaca.training.adapter.out.persistence.ActivityRepository
import cat.subi.itaca.training.adapter.out.persistence.StravaAccountEntity
import cat.subi.itaca.training.adapter.out.persistence.StravaAccountRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Strava sync against a WireMock-stubbed Strava API: token refresh + activities import. */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class StravaSyncIntegrationTest {
    @Autowired
    lateinit var strava: StravaService

    @Autowired
    lateinit var queries: ActivityQueries

    @Autowired
    lateinit var accounts: StravaAccountRepository

    @Autowired
    lateinit var activities: ActivityRepository

    @AfterEach
    fun clean() {
        activities.deleteAll()
        accounts.deleteAll()
    }

    @Test
    fun `refreshes the token and imports bike, run and hike activities`() {
        stubToken()
        stubActivities()
        // Connected with an expired access token -> sync must refresh first.
        accounts.save(
            StravaAccountEntity(
                refreshToken = "rt-old",
                expiresAt = Instant.now().minusSeconds(3600),
                accessToken = null,
            ),
        )

        val result = strava.sync()
        assertEquals(3, result.imported)
        assertEquals(3, result.total)

        val view = queries.view()
        assertTrue(view.connected)
        assertEquals(3, view.activities.size)
        val bike = view.activities.single { it.type == "bike" }
        assertEquals(42.3, bike.distanceKm)
        assertEquals(setOf("bike", "run", "hike"), view.activities.map { it.type }.toSet())

        val bikeVol = view.volume.getValue("bike")
        assertEquals(8, bikeVol.weeks.size)
        assertEquals("esta", bikeVol.weeks.last().label)
        assertEquals("km", bikeVol.unit)
        assertEquals("h", view.volume.getValue("gym").unit)
        // YTD carries km + elevation + time for endurance sports, time only for gym.
        assertTrue(bikeVol.ytd.distance!!.endsWith("km"))
        assertTrue(bikeVol.ytd.elevation!!.endsWith("m D+"))
        assertTrue(bikeVol.ytd.time.endsWith("h"))
        assertEquals(
            null,
            view.volume
                .getValue("gym")
                .ytd.distance,
        )

        // A second sync of the same data must not duplicate.
        assertEquals(0, strava.sync().imported)
        assertEquals(3, activities.count().toInt())
    }

    @Test
    fun `bootstraps the account from the configured refresh token when none is stored`() {
        stubToken()
        stubActivities()
        // No account saved: the env-configured refresh token must seed it on sync.
        assertEquals(0, accounts.count())
        assertTrue(strava.isConnected())

        val result = strava.sync()

        assertEquals(3, result.imported)
        assertEquals(1, accounts.count())
    }

    @Test
    fun `maps a Strava weight-training session to the gym type`() {
        stubToken()
        wireMock.stubFor(
            get(urlPathEqualTo("/athlete/activities")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    """
                    [{"id": 2001, "name":"Fuerza", "sport_type":"WeightTraining",
                      "start_date":"2026-06-10T18:00:00Z", "distance": 0.0, "moving_time": 3720,
                      "average_heartrate": 87.0}]
                    """.trimIndent(),
                ),
            ),
        )
        accounts.save(StravaAccountEntity(refreshToken = "rt", expiresAt = Instant.now().minusSeconds(3600)))

        strava.sync()

        val act = queries.view().activities.single()
        assertEquals("gym", act.type)
        assertEquals("WeightTraining", act.sport)
    }

    private fun stubToken() {
        wireMock.stubFor(
            post(urlPathEqualTo("/oauth/token")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    """
                    {"token_type":"Bearer","access_token":"at123","refresh_token":"rt456",
                     "expires_at": 9999999999, "expires_in": 21600, "athlete": {"id": 42}}
                    """.trimIndent(),
                ),
            ),
        )
    }

    private fun stubActivities() {
        wireMock.stubFor(
            get(urlPathEqualTo("/athlete/activities")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    """
                    [
                      {"id": 1001, "name":"Ruta", "sport_type":"Ride", "type":"Ride",
                       "start_date":"2026-06-12T07:00:00Z", "distance": 42300.0, "moving_time": 5880,
                       "total_elevation_gain": 620.0, "average_heartrate": 142.0, "average_speed": 7.2},
                      {"id": 1002, "name":"Tirada", "sport_type":"Run",
                       "start_date":"2026-06-10T18:00:00Z", "distance": 8200.0, "moving_time": 2670,
                       "total_elevation_gain": 60.0, "average_heartrate": 156.0, "average_speed": 3.07},
                      {"id": 1003, "name":"Montaña", "sport_type":"Hike",
                       "start_date":"2026-06-08T09:00:00Z", "distance": 11000.0, "moving_time": 11400,
                       "total_elevation_gain": 780.0, "average_speed": 0.96}
                    ]
                    """.trimIndent(),
                ),
            ),
        )
    }

    companion object {
        private val wireMock = WireMockServer(wireMockConfig().dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stop() = wireMock.stop()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("itaca.strava.client-id") { "test-id" }
            registry.add("itaca.strava.client-secret") { "test-secret" }
            registry.add("itaca.strava.refresh-token") { "rt-seed" }
            registry.add("itaca.strava.auth-base") { wireMock.baseUrl() }
            registry.add("itaca.strava.api-base") { wireMock.baseUrl() }
        }
    }
}
