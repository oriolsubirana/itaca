/**
 * Wellness bounded context: daily Garmin metrics (sleep, HRV, recovery) pushed by an
 * external sync via POST /api/wellness/daily, exposed to the chat read-only. Records and
 * describes data only — no medical interpretation (see the health domain rule).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Wellness")
package cat.subi.itaca.wellness;
