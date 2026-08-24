// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.wellness.adapter.`in`.rest

import cat.subi.itaca.wellness.application.BodyCompositionCommand
import cat.subi.itaca.wellness.application.BodyCompositionDto
import cat.subi.itaca.wellness.application.BodyCompositionService
import cat.subi.itaca.wellness.application.BodyCompositionSummary
import cat.subi.itaca.wellness.application.WellnessCommand
import cat.subi.itaca.wellness.application.WellnessDayDto
import cat.subi.itaca.wellness.application.WellnessService
import cat.subi.itaca.wellness.application.WellnessSummary
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** A day's Garmin metrics as pushed by the external sync; all metrics optional. */
data class WellnessRequest(
    val date: String = "",
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepScore: Int? = null,
    val hrvAvgMs: Int? = null,
    val hrvStatus: String? = null,
    val restingHr: Int? = null,
    val stressAvg: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val steps: Int? = null,
    val activeCalories: Int? = null,
    val spo2Avg: Int? = null,
    val respirationAvg: Double? = null,
) {
    fun toCommand(): WellnessCommand =
        WellnessCommand(
            date = LocalDate.parse(date),
            sleepMinutes = sleepMinutes,
            deepMinutes = deepMinutes,
            lightMinutes = lightMinutes,
            remMinutes = remMinutes,
            awakeMinutes = awakeMinutes,
            sleepScore = sleepScore,
            hrvAvgMs = hrvAvgMs,
            hrvStatus = hrvStatus,
            restingHr = restingHr,
            stressAvg = stressAvg,
            bodyBatteryHigh = bodyBatteryHigh,
            bodyBatteryLow = bodyBatteryLow,
            steps = steps,
            activeCalories = activeCalories,
            spo2Avg = spo2Avg,
            respirationAvg = respirationAvg,
        )
}

/** One scale measurement as pushed by the external Zepp sync; composition fields optional. */
data class BodyCompositionRequest(
    val date: String = "",
    val weightKg: Double = 0.0,
    val bmi: Double? = null,
    val bodyFatPct: Double? = null,
    val muscleKg: Double? = null,
    val waterPct: Double? = null,
    val boneKg: Double? = null,
    val visceralFat: Double? = null,
    val bmrKcal: Int? = null,
) {
    fun toCommand(): BodyCompositionCommand {
        require(weightKg > 0) { "weightKg must be positive" }
        return BodyCompositionCommand(
            date = LocalDate.parse(date),
            weightKg = weightKg,
            bmi = bmi,
            bodyFatPct = bodyFatPct,
            muscleKg = muscleKg,
            waterPct = waterPct,
            boneKg = boneKg,
            visceralFat = visceralFat,
            bmrKcal = bmrKcal,
        )
    }
}

@RestController
@RequestMapping("/api/wellness")
class WellnessController(
    private val wellness: WellnessService,
    private val body: BodyCompositionService,
) {
    /** Upsert a scale measurement (idempotent on the date). Fed by the external Zepp sync. */
    @PostMapping("/body")
    fun upsertBody(
        @RequestBody request: BodyCompositionRequest,
    ): BodyCompositionDto = body.upsert(request.toCommand())

    @GetMapping("/body")
    fun bodyRecent(
        @RequestParam(required = false) days: Int?,
    ): BodyCompositionSummary = body.queryBodyComposition(days)

    /** Upsert a day's metrics (idempotent on the date). Fed by the external Garmin sync. */
    @PostMapping("/daily")
    fun daily(
        @RequestBody request: WellnessRequest,
    ): WellnessDayDto = wellness.upsert(request.toCommand())

    @GetMapping
    fun recent(
        @RequestParam(required = false) days: Int?,
    ): WellnessSummary = wellness.queryWellness(days ?: DEFAULT_DAYS)

    @ExceptionHandler(IllegalArgumentException::class, java.time.format.DateTimeParseException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: Exception): Map<String, String?> = mapOf("error" to e.message)

    private companion object {
        const val DEFAULT_DAYS = 14
    }
}
