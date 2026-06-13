// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.StravaService
import cat.subi.itaca.training.application.SyncResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * Strava connection. /connect and /callback are browser redirects (no bearer
 * token — they are exempted in BearerTokenFilter); /sync and /status are normal
 * authenticated API calls from the app.
 */
@RestController
@RequestMapping("/api/strava")
class StravaController(
    private val strava: StravaService,
) {
    @GetMapping("/connect")
    fun connect(): ResponseEntity<Void> = redirect(strava.authorizeUrl())

    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val outcome =
            if (code != null && error == null && runCatching { strava.connect(code) }.isSuccess) {
                "connected"
            } else {
                "error"
            }
        return redirect("${strava.appUrl}/gym?strava=$outcome")
    }

    private fun redirect(url: String): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(url))
            .build()

    @GetMapping("/status")
    fun status(): Map<String, Boolean> = mapOf("connected" to strava.isConnected())

    @PostMapping("/sync")
    fun sync(): SyncResult = strava.sync()
}
