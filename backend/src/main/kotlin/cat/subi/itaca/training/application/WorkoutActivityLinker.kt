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
            WITH unlinked_workouts AS (
                SELECT id, date, row_number() OVER (PARTITION BY date ORDER BY id) AS rn
                FROM workouts WHERE strava_id IS NULL
            ),
            unlinked_activities AS (
                SELECT a.strava_id,
                       (a.start_date AT TIME ZONE '$ZONE')::date AS day,
                       row_number() OVER (
                           PARTITION BY (a.start_date AT TIME ZONE '$ZONE')::date ORDER BY a.start_date
                       ) AS rn
                FROM activities a
                WHERE a.type = 'gym'
                  AND NOT EXISTS (SELECT 1 FROM workouts w2 WHERE w2.strava_id = a.strava_id)
            )
            UPDATE workouts w SET strava_id = ua.strava_id
            FROM unlinked_workouts uw
            JOIN unlinked_activities ua ON ua.day = uw.date AND ua.rn = uw.rn
            WHERE w.id = uw.id
            """.trimIndent(),
        )

    private companion object {
        // Match WorkoutActivityLinker/ActivityQueries: bucket the UTC timestamp into this zone's day.
        const val ZONE = "Europe/Madrid"
    }
}
