// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.LabNormalizationService
import cat.subi.itaca.health.application.RenormalizeResult
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Re-runs the dictionary matching over already-stored results (no AI call), so
 * reports confirmed before new synonyms were added pick them up.
 */
@RestController
@RequestMapping("/api/health")
class LabNormalizationController(
    private val normalization: LabNormalizationService,
) {
    @PostMapping("/lab-reports/{id}/renormalize")
    fun renormalize(
        @PathVariable id: Long,
    ): RenormalizeResult = normalization.renormalize(id)

    @PostMapping("/lab-reports/renormalize")
    fun renormalizeAll(): RenormalizeResult = normalization.renormalizeAll()

    /** Deterministic pass plus a semantic (AI) pass for the multilingual long tail. */
    @PostMapping("/lab-reports/renormalize/ai")
    fun semanticRenormalize(): RenormalizeResult = normalization.semanticRenormalize()

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)
}
