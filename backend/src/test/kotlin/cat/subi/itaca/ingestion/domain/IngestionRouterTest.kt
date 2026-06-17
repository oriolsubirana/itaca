package cat.subi.itaca.ingestion.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Pure deterministic routing: cheap filename/type rules; ambiguous PDFs defer to the AI tier. */
class IngestionRouterTest {
    @Test
    fun `a CSV is always a bank statement (Neon is the only CSV source)`() {
        assertEquals(Destination.FINANCE_BANK, IngestionRouter.route("transactions-2026.csv", FileType.CSV))
    }

    @Test
    fun `a finpension performance PDF routes to finance`() {
        assertEquals(Destination.FINANCE_BANK, IngestionRouter.route("performance-report.pdf", FileType.PDF))
        assertEquals(Destination.FINANCE_BANK, IngestionRouter.route("finpension-2026-05.pdf", FileType.PDF))
    }

    @Test
    fun `a lab-named PDF routes to health`() {
        assertEquals(Destination.HEALTH_LAB, IngestionRouter.route("analitica-enero.pdf", FileType.PDF))
        assertEquals(Destination.HEALTH_LAB, IngestionRouter.route("Analisis-de-sangre.pdf", FileType.PDF))
    }

    @Test
    fun `a generic PDF is left UNKNOWN so the AI tier can classify it`() {
        assertEquals(Destination.UNKNOWN, IngestionRouter.route("document.pdf", FileType.PDF))
    }

    @Test
    fun `a non-PDF non-CSV file is UNKNOWN`() {
        assertEquals(Destination.UNKNOWN, IngestionRouter.route("notes.txt", FileType.UNKNOWN))
    }
}
