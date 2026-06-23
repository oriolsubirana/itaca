/**
 * Google bounded context: reads the user's Google data (Calendar now; Gmail/Drive later) over the
 * REST APIs using the OAuth tokens obtained at login and persisted by the auth phase. Exposed to
 * the chat read-only via {@code ChatTools}. Records and describes data only.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Google")
package cat.subi.itaca.google;
