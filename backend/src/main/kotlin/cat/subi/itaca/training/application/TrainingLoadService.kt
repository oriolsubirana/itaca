package cat.subi.itaca.training.application

import cat.subi.itaca.shared.chat.ChatTools
import cat.subi.itaca.training.domain.FormStatus
import cat.subi.itaca.training.domain.HeartRateProfile
import cat.subi.itaca.training.domain.LoadPoint
import cat.subi.itaca.training.domain.TrainingLoad
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.roundToInt

data class LoadPointView(
    val date: String,
    val load: Int,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
)

data class TrainingLoadView(
    val today: LoadPointView,
    /** fresh | neutral | productive | overreaching */
    val status: String,
    val statusLabel: String,
    val advice: String,
    /** Fitness change over the last 7 days (CTL points/week). */
    val rampPerWeek: Double,
    val rampWarning: Boolean,
    val series: List<LoadPointView>,
    val note: String,
)

/**
 * Fitness / fatigue / form (CTL / ATL / TSB) from the imported Strava activities. Combined with
 * the Garmin recovery signals at the edge (chat, frontend) — this context doesn't read wellness.
 */
@Service
class TrainingLoadService(
    private val ledger: TrainingLedger,
    @Value("\${itaca.training.resting-hr:55}") restingHr: Int,
    @Value("\${itaca.training.max-hr:185}") maxHr: Int,
) : ChatTools {
    private val hr = HeartRateProfile(restingHr, maxHr)

    @Tool(
        name = "query_training_load",
        description =
            "Training load model from Strava: fitness (CTL, 42-day), fatigue (ATL, 7-day) and form (TSB = " +
                "fitness - fatigue) on a TSS-like scale, today's status (fresh / neutral / productive / " +
                "overreaching), the weekly fitness ramp with a warning when it climbs too fast, and the daily " +
                "series. Use it to judge whether to push, hold or back off, and to steer the taper. Combine it " +
                "with query_wellness (HRV, sleep) before advising.",
    )
    fun queryLoad(
        @ToolParam(description = "Days of series to return (default 42)", required = false) days: Int?,
    ): TrainingLoadView = view(LocalDate.now(), days ?: DEFAULT_DAYS)

    fun view(
        today: LocalDate,
        days: Int,
    ): TrainingLoadView {
        val window = days.coerceIn(MIN_DAYS, MAX_DAYS)
        val from = today.minusDays(window - 1L)
        val warmUpFrom = from.minusDays(WARM_UP_DAYS)
        val daily =
            ledger
                .activities(warmUpFrom, today)
                .groupBy { it.date }
                .mapValues { (_, acts) -> acts.sumOf { TrainingLoad.sessionLoad(it.sport, it.minutes, it.avgHr, hr) } }
        // Anchor the warm-up start so the averages build up over the 90 days before the window.
        val series = TrainingLoad.series(daily + (warmUpFrom to (daily[warmUpFrom] ?: 0.0)), from, today)
        val last = series.last()
        val weekAgo = series.getOrNull(series.size - 1 - DAYS_PER_WEEK)
        val ramp = weekAgo?.let { last.ctl - it.ctl } ?: 0.0
        val status = TrainingLoad.formStatus(last.tsb)
        return TrainingLoadView(
            today = last.toView(),
            status = status.name.lowercase(),
            statusLabel = LABELS.getValue(status),
            advice = ADVICE.getValue(status),
            rampPerWeek = round1(ramp),
            rampWarning = ramp > MAX_SAFE_RAMP,
            series = series.map { it.toView() },
            note =
                "Modelo orientativo (1 h a umbral = 100). " +
                    "FC de referencia: reposo ${hr.restingHr}, máx ${hr.maxHr} (itaca.training.resting-hr / max-hr). " +
                    "Natación estimada sin pulso.",
        )
    }

    private fun LoadPoint.toView(): LoadPointView {
        val (fitness, fatigue, form) = listOf(ctl, atl, tsb).map(::round1)
        return LoadPointView(date.toString(), load.roundToInt(), fitness, fatigue, form)
    }

    private fun round1(v: Double): Double = (v * TENTHS).roundToInt() / TENTHS

    private companion object {
        const val DEFAULT_DAYS = 42
        const val MIN_DAYS = 7
        const val MAX_DAYS = 365
        const val WARM_UP_DAYS = 90L
        const val DAYS_PER_WEEK = 7
        const val MAX_SAFE_RAMP = 6.0
        const val TENTHS = 10.0

        val LABELS =
            mapOf(
                FormStatus.FRESH to "Fresco",
                FormStatus.NEUTRAL to "Equilibrado",
                FormStatus.PRODUCTIVE to "Cargando",
                FormStatus.OVERREACHING to "Sobrecarga",
            )
        val ADVICE =
            mapOf(
                FormStatus.FRESH to
                    "Buenas piernas para una sesión clave o una prueba; si dura semanas, se pierde forma.",
                FormStatus.NEUTRAL to "Zona de mantenimiento: sigue el plan tal cual.",
                FormStatus.PRODUCTIVE to
                    "Carga productiva: el plan está haciendo su trabajo. Cuida el sueño y la comida.",
                FormStatus.OVERREACHING to
                    "Fatiga alta: riesgo de lesión o enfermedad. " +
                    "Recorta o cambia la próxima sesión clave por algo suave.",
            )
    }
}
