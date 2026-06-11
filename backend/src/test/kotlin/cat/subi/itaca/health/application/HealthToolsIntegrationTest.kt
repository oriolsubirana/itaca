package cat.subi.itaca.health.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class HealthToolsIntegrationTest {
    @Autowired
    lateinit var tools: HealthTools

    @Test
    fun `diary entries are upserted by date, merging only the provided fields`() {
        val first = tools.logDiaryEntry(null, 4, null, null, null, 2, null, null)
        assertTrue(first.saved)
        assertEquals(4, first.entry!!.bristol)
        assertEquals(2, first.entry!!.bowelMovements)

        val second = tools.logDiaryEntry(null, null, 3, null, true, null, null, "día regular")
        assertTrue(second.saved)
        val entry = second.entry!!
        assertEquals(4, entry.bristol, "previous fields must survive the merge")
        assertEquals(3, entry.pain)
        assertEquals(true, entry.blood)
        assertEquals("día regular", entry.notes)
    }

    @Test
    fun `rejects out-of-scale values with an actionable error`() {
        val result = tools.logDiaryEntry(null, 9, null, null, null, null, null, null)
        assertFalse(result.saved)
        assertTrue(result.error!!.contains("Bristol"))
    }

    @Test
    fun `flare lifecycle - start once, idempotent start, end, and no double end`() {
        val started = tools.logFlare("start", "moderate", "2026-06-01", "tras analítica")
        assertTrue(started.saved)
        assertEquals("moderate", started.flare!!.severity)

        val startedAgain = tools.logFlare("start", "severe", null, null)
        assertEquals(started.flare!!.id, startedAgain.flare!!.id, "starting twice must not open a second flare")

        assertNotNull(tools.queryHealth(0).activeFlare)

        val ended = tools.logFlare("end", null, "2026-06-10", null)
        assertTrue(ended.saved)
        assertEquals("2026-06-10", ended.flare!!.endDate)
        assertNull(tools.queryHealth(0).activeFlare)

        val endedAgain = tools.logFlare("end", null, null, null)
        assertFalse(endedAgain.saved)
    }

    @Test
    fun `rejects ending a flare before it started and unknown severities`() {
        tools.logFlare("start", "mild", "2026-06-05", null)
        val badEnd = tools.logFlare("end", null, "2026-06-01", null)
        assertFalse(badEnd.saved)

        tools.logFlare("end", null, "2026-06-06", null)
        val badSeverity = tools.logFlare("start", "apocalíptico", null, null)
        assertFalse(badSeverity.saved)
        assertTrue(badSeverity.error!!.contains("severity", ignoreCase = true))
    }

    @Test
    fun `query_health returns recent entries newest first`() {
        tools.logDiaryEntry("2026-06-10", 5, 2, null, null, 3, null, null)
        tools.logDiaryEntry("2026-06-11", 4, 1, null, null, 2, null, null)

        val summary = tools.queryHealth(30)

        assertEquals(listOf("2026-06-11", "2026-06-10"), summary.recentEntries.map { it.date })
    }
}
