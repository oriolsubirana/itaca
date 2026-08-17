package cat.subi.itaca.google.adapter.out.persistence

import cat.subi.itaca.google.application.DriveSeenStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcDriveSeenStore(
    private val jdbc: JdbcTemplate,
) : DriveSeenStore {
    override fun isSeen(fileId: String): Boolean =
        (jdbc.queryForObject("SELECT count(*) FROM drive_seen WHERE file_id = ?", Int::class.java, fileId) ?: 0) > 0

    override fun markSeen(
        fileId: String,
        name: String,
        mimeType: String,
    ) {
        jdbc.update(
            "INSERT INTO drive_seen (file_id, name, mime_type) VALUES (?, ?, ?) ON CONFLICT (file_id) DO NOTHING",
            fileId,
            name,
            mimeType,
        )
    }
}
