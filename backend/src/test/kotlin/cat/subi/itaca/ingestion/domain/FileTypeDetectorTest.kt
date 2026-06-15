package cat.subi.itaca.ingestion.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** File type from magic bytes first, then extension — never trusts the client-declared type. */
class FileTypeDetectorTest {
    @Test
    fun `PDF is detected by its magic bytes even with the wrong extension`() {
        assertEquals(FileType.PDF, FileTypeDetector.detect("mislabelled.csv", "%PDF-1.7 ...".toByteArray()))
    }

    @Test
    fun `CSV is detected by extension`() {
        assertEquals(FileType.CSV, FileTypeDetector.detect("neon.csv", "\"Date\";\"Amount\"".toByteArray()))
    }

    @Test
    fun `anything else is UNKNOWN`() {
        assertEquals(FileType.UNKNOWN, FileTypeDetector.detect("notes.txt", "just text".toByteArray()))
    }
}
