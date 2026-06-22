package cat.subi.itaca.google.application

import cat.subi.itaca.ingestion.DocumentInbox
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeReader(
    private val files: List<DriveDoc>,
    private val failIds: Set<String> = emptySet(),
) : DriveReader {
    override fun listFolder(
        accessToken: String,
        folderId: String,
    ) = files

    override fun download(
        accessToken: String,
        fileId: String,
    ): ByteArray {
        if (fileId in failIds) error("boom")
        return "content-of-$fileId".toByteArray()
    }
}

private class FakeSeen(
    val seen: MutableSet<String> = mutableSetOf(),
) : DriveSeenStore {
    override fun isSeen(fileId: String) = fileId in seen

    override fun markSeen(
        fileId: String,
        name: String,
        mimeType: String,
    ) {
        seen += fileId
    }
}

private class FakeInbox : DocumentInbox {
    val received = mutableListOf<String>()

    override fun receive(
        source: String,
        filename: String,
        content: ByteArray,
    ): Long {
        received += filename
        return received.size.toLong()
    }
}

class DriveSyncServiceTest {
    @Test
    fun `ingests new downloadable files once and marks them seen`() {
        val reader =
            FakeReader(
                listOf(
                    DriveDoc("1", "labs.pdf", "application/pdf"),
                    DriveDoc("2", "bank.csv", "text/csv"),
                ),
            )
        val seen = FakeSeen()
        val inbox = FakeInbox()

        val result = syncDriveFolder("tok", "folder", reader, seen, inbox)

        assertEquals(2, result.ingested)
        assertEquals(2, result.listed)
        assertEquals(listOf("labs.pdf", "bank.csv"), inbox.received)
        assertTrue(seen.seen.containsAll(setOf("1", "2")))
    }

    @Test
    fun `skips files already seen`() {
        val reader = FakeReader(listOf(DriveDoc("1", "labs.pdf", "application/pdf")))
        val seen = FakeSeen(mutableSetOf("1"))
        val inbox = FakeInbox()

        val result = syncDriveFolder("tok", "folder", reader, seen, inbox)

        assertEquals(0, result.ingested)
        assertTrue(inbox.received.isEmpty())
    }

    @Test
    fun `skips Google-native docs but marks them seen so they aren't re-listed`() {
        val reader = FakeReader(listOf(DriveDoc("1", "Hoja", "application/vnd.google-apps.spreadsheet")))
        val seen = FakeSeen()
        val inbox = FakeInbox()

        val result = syncDriveFolder("tok", "folder", reader, seen, inbox)

        assertEquals(0, result.ingested)
        assertTrue(inbox.received.isEmpty())
        assertTrue(seen.isSeen("1"))
    }

    @Test
    fun `a failed download is reported and not marked seen, others still ingest`() {
        val reader =
            FakeReader(
                listOf(
                    DriveDoc("1", "good.pdf", "application/pdf"),
                    DriveDoc("2", "bad.pdf", "application/pdf"),
                ),
                failIds = setOf("2"),
            )
        val seen = FakeSeen()
        val inbox = FakeInbox()

        val result = syncDriveFolder("tok", "folder", reader, seen, inbox)

        assertEquals(1, result.ingested)
        assertEquals(listOf("bad.pdf"), result.failed)
        assertTrue(seen.isSeen("1"))
        assertFalse(seen.isSeen("2"))
    }
}
