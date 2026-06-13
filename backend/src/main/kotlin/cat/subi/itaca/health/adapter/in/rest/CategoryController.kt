// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.health.application.CategoryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CategoryUpdate(
    val category: String? = null,
)

/** Sets the theme category on a report or document (user override of Claude's guess). */
@RestController
@RequestMapping("/api/health")
class CategoryController(
    private val categories: CategoryService,
) {
    @PutMapping("/lab-reports/{id}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setReportCategory(
        @PathVariable id: Long,
        @RequestBody update: CategoryUpdate,
    ) = categories.setReportCategory(id, update.category)

    @PutMapping("/medical-documents/{id}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setDocumentCategory(
        @PathVariable id: Long,
        @RequestBody update: CategoryUpdate,
    ) = categories.setDocumentCategory(id, update.category)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
