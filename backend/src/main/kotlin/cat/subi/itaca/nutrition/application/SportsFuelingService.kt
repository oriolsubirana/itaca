package cat.subi.itaca.nutrition.application

import cat.subi.itaca.nutrition.domain.DayFuelingTarget
import cat.subi.itaca.nutrition.domain.FuelingStep
import cat.subi.itaca.nutrition.domain.SessionFueling
import cat.subi.itaca.nutrition.domain.SportsFueling
import cat.subi.itaca.nutrition.domain.TrainingDayType
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

data class DayTargetView(
    /** rest | easy | key | long | race — the training plan's day load. */
    val dayType: String,
    val label: String,
    val carbsGPerKg: String,
    val proteinGPerKg: String,
    val carbsGPerKgMin: Double,
    val carbsGPerKgMax: Double,
    val proteinGPerKgMin: Double,
    val proteinGPerKgMax: Double,
    val guidance: String,
)

data class SportsFuelingView(
    /** The day type asked for (when given), its target first-class for the chat. */
    val today: DayTargetView?,
    val dayTargets: List<DayTargetView>,
    val carbSources: String,
    val sessionFueling: List<SessionFueling>,
    val raceWeek: List<FuelingStep>,
    val principles: List<String>,
    val disclaimer: String,
)

/** Sports fueling on top of the Paleomind plan, as a chat tool and for the Comidas/Triatlón screens. */
@Service
class SportsFuelingService : ChatTools {
    @Tool(
        name = "query_sports_fueling",
        description =
            "Sports nutrition for the triathlon build, layered on the Paleomind diet: carbohydrate and protein " +
                "targets in g/kg by training day type (rest / easy / key / long / race — take the day's type " +
                "from query_training_week or query_triathlon_plan), what to eat before/during/after a session " +
                "by its duration, the race-week protocol (carb loading, low-residue 48 h, race breakfast) and " +
                "principles. Use it when proposing the day's meals or answering fueling questions; multiply " +
                "g/kg by the profile weight to give grams.",
    )
    fun queryFueling(
        @ToolParam(description = "Today's training day type: rest, easy, key, long or race", required = false)
        dayType: String?,
    ): SportsFuelingView =
        SportsFuelingView(
            today = dayType?.let(TrainingDayType::fromWire)?.let { SportsFueling.targetFor(it).toView() },
            dayTargets = SportsFueling.dayTargets.map { it.toView() },
            carbSources = SportsFueling.CARB_SOURCES,
            sessionFueling = SportsFueling.sessionFueling,
            raceWeek = SportsFueling.raceWeek,
            principles = SportsFueling.principles,
            disclaimer = SportsFueling.DISCLAIMER,
        )

    private fun DayFuelingTarget.toView() =
        DayTargetView(
            dayType = dayType.wire,
            label = label,
            carbsGPerKg = "${fmt(carbsGPerKgMin)}-${fmt(carbsGPerKgMax)} g/kg",
            proteinGPerKg = "${fmt(proteinGPerKgMin)}-${fmt(proteinGPerKgMax)} g/kg",
            carbsGPerKgMin = carbsGPerKgMin,
            carbsGPerKgMax = carbsGPerKgMax,
            proteinGPerKgMin = proteinGPerKgMin,
            proteinGPerKgMax = proteinGPerKgMax,
            guidance = guidance,
        )

    private fun fmt(v: Double): String = (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()).replace('.', ',')
}
