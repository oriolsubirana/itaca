// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.nutrition.adapter.`in`.rest

import cat.subi.itaca.nutrition.application.MealAnalysis
import cat.subi.itaca.nutrition.application.MealCommand
import cat.subi.itaca.nutrition.application.MealDto
import cat.subi.itaca.nutrition.application.MealsSummary
import cat.subi.itaca.nutrition.application.NutritionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

data class LogMealRequest(
    val date: String? = null,
    val mealType: String = "",
    val description: String = "",
    val onPlan: Boolean? = null,
    val calories: Int? = null,
    val notes: String? = null,
) {
    fun toCommand(): MealCommand =
        MealCommand(
            date = date?.let(LocalDate::parse) ?: LocalDate.now(),
            mealType = mealType,
            description = description,
            onPlan = onPlan,
            calories = calories,
            notes = notes,
        )
}

@RestController
@RequestMapping("/api/nutrition")
class NutritionController(
    private val nutrition: NutritionService,
) {
    @GetMapping("/meals")
    fun meals(
        @RequestParam(required = false) days: Int?,
    ): MealsSummary = nutrition.recent(days ?: DEFAULT_DAYS)

    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    fun log(
        @RequestBody request: LogMealRequest,
    ): MealDto = nutrition.save(request.toCommand())

    /** Analyses a meal photo and returns a proposal; the client reviews it, then POSTs to /meals. */
    @PostMapping("/meals/photo")
    fun analyzePhoto(
        @RequestParam("file") file: MultipartFile,
    ): MealAnalysis {
        require(!file.isEmpty) { "Empty file" }
        return nutrition.analyzePhoto(file.bytes, file.contentType ?: "image/jpeg")
    }

    @DeleteMapping("/meals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) = nutrition.delete(id)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)

    private companion object {
        const val DEFAULT_DAYS = 14
    }
}
