package cat.subi.itaca.training.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Links a logged strength workout to the Strava gym activity of the same day.
 * Both tables live in the training context, so this is a plain same-module join
 * (no cross-context events). Idempotent: only fills still-unlinked workouts and
 * never reuses an activity already linked to another workout. Runs after a sync,
 * when fresh gym activities have just been imported.
 */
@Service
class WorkoutActivityLinker(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun linkByDate(): Int =
        jdbc.update(
            """
            UPDATE workouts w SET strava_id = a.strava_id
            FROM activities a
            WHERE a.type = 'gym'
              AND (a.start_date AT TIME ZONE ?)::date = w.date
              AND w.strava_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM workouts w2 WHERE w2.strava_id = a.strava_id)
            """.trimIndent(),
            ZONE,
        )

    private companion object {
        const val ZONE = "Europe/Madrid"
    }
}
