// Durations, distances and progression steps below are the coaching plan itself (domain data),
// not tunable magic constants — same convention as TriathlonPlan and CalorieCalculator.
@file:Suppress("MagicNumber")

package cat.subi.itaca.training.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Sport { SWIM, BIKE, RUN, GYM, RACE }

enum class Intensity { EASY, MODERATE, KEY, LONG, RACE }

/** 3+1 mesocycles (LOAD ×3, RECOVERY ×1), then the last four weeks: SHARPEN ×2, TAPER, RACE. */
enum class WeekKind { LOAD, RECOVERY, SHARPEN, TAPER, RACE }

/**
 * How demanding a day is, the contract the edge (frontend, chat) uses to pick the day's fueling.
 * The nutrition context keys its sports-fueling guidance by the same wire codes.
 */
enum class DayLoad {
    REST,
    EASY,
    KEY,
    LONG,
    RACE,
    ;

    val wire: String get() = name.lowercase()
}

data class PlannedSession(
    val date: LocalDate,
    val sport: Sport,
    val title: String,
    val detail: String,
    val minutes: Int,
    val intensity: Intensity,
    /** Gym routine to run (Push/Pull/Leg) — only for GYM sessions. */
    val gymRoutine: String? = null,
    /** Target distance, set on the long runs (the run-volume progression). */
    val distanceKm: Double? = null,
)

data class PlanWeek(
    val monday: LocalDate,
    val phaseKey: String,
    val weekInPhase: Int,
    val weeksToRace: Int,
    val kind: WeekKind,
    val focus: String,
    val sessions: List<PlannedSession>,
) {
    val totalMinutes: Int get() = sessions.sumOf { it.minutes }

    fun sessionsOn(date: LocalDate): List<PlannedSession> = sessions.filter { it.date == date }
}

/**
 * The week-by-week schedule of "Camino al sub-2h30": every week from the plan start to race day,
 * Monday to Sunday, generated deterministically from the phase (TriathlonPlan), the position in
 * the 3+1 mesocycle and the weeks left to the race. User-facing strings are Spanish.
 *
 * Gym stays inside the project rules: working sets 3×6-8 with 90 s rest, conservative progression.
 */
object TrainingSchedule {
    private val firstMonday: LocalDate = TriathlonPlan.phases.first().start
    private val raceMonday: LocalDate = TriathlonPlan.raceDate.with(DayOfWeek.MONDAY)
    private val gymRotation = listOf("Push", "Pull", "Leg")

    fun weekOf(date: LocalDate): PlanWeek? {
        if (date.isBefore(firstMonday) || date.isAfter(TriathlonPlan.raceDate)) return null
        return build(date.with(DayOfWeek.MONDAY))
    }

    fun sessionsOn(date: LocalDate): List<PlannedSession> = weekOf(date)?.sessionsOn(date).orEmpty()

    fun dayLoadOn(date: LocalDate): DayLoad = dayLoadOf(sessionsOn(date))

    fun dayLoadOf(sessions: List<PlannedSession>): DayLoad =
        when {
            sessions.any { it.intensity == Intensity.RACE } -> DayLoad.RACE
            sessions.any { it.intensity == Intensity.LONG } || sessions.sumOf { it.minutes } >= 120 -> DayLoad.LONG
            sessions.any { it.intensity == Intensity.KEY } -> DayLoad.KEY
            sessions.isNotEmpty() -> DayLoad.EASY
            else -> DayLoad.REST
        }

    fun allWeeks(): List<PlanWeek> =
        generateSequence(firstMonday) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(raceMonday) }
            .map(::build)
            .toList()

    private fun build(monday: LocalDate): PlanWeek {
        val phase = TriathlonPlan.phaseOn(monday)!!
        val phaseMonday = phase.start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val weekInPhase = (ChronoUnit.WEEKS.between(phaseMonday, monday) + 1).toInt()
        val weeksToRace = ChronoUnit.WEEKS.between(monday, raceMonday).toInt()
        val kind =
            when {
                weeksToRace == 0 -> WeekKind.RACE
                weeksToRace == 1 -> WeekKind.TAPER
                weeksToRace <= 3 -> WeekKind.SHARPEN
                weekInPhase % 4 == 0 -> WeekKind.RECOVERY
                else -> WeekKind.LOAD
            }
        // Progression index: load weeks completed so far in the phase (a recovery week reuses the last).
        val step = weekInPhase - weekInPhase / 4
        val ctx = WeekContext(monday, weekInPhase, step, kind == WeekKind.RECOVERY)
        val (focus, sessions) =
            when (kind) {
                WeekKind.RACE -> RACE_FOCUS to raceWeek(ctx)
                WeekKind.TAPER -> TAPER_FOCUS to taperWeek(ctx)
                WeekKind.SHARPEN ->
                    if (weeksToRace == 2) SIMULATION_FOCUS to simulationWeek(ctx) else SHARPEN_FOCUS to sharpenWeek(ctx)
                else ->
                    when (phase.key) {
                        "base" -> focusOf(phase.name, ctx) to baseWeek(ctx)
                        "winter" -> focusOf(phase.name, ctx) to winterWeek(ctx)
                        else -> focusOf(phase.name, ctx) to specificWeek(ctx)
                    }
            }
        return PlanWeek(monday, phase.key, weekInPhase, weeksToRace, kind, focus, sessions)
    }

    private data class WeekContext(
        val monday: LocalDate,
        val weekInPhase: Int,
        val step: Int,
        val recovery: Boolean,
    ) {
        fun day(dow: DayOfWeek): LocalDate = monday.with(dow)
    }

    private fun focusOf(
        phaseName: String,
        ctx: WeekContext,
    ): String =
        if (ctx.recovery) {
            "$phaseName · descarga: −35 % de volumen y todo suave. Aquí se asimila el trabajo de las 3 semanas " +
                "anteriores."
        } else {
            "$phaseName · semana de carga ${(ctx.weekInPhase - 1) % 4 + 1} de 3"
        }

    // ---------------------------------------------------------------- Base: volume and technique

    private fun baseWeek(c: WeekContext): List<PlannedSession> {
        val r = c.recovery
        val longKm = if (r) roundHalf(baseLongKm(c.step) * 0.7) else baseLongKm(c.step)
        val continuousM = roundTo(min(1000, 300 + 75 * c.step), 50)
        return listOf(
            c.swim(DayOfWeek.MONDAY, "Natación · técnica", TECHNIQUE_DRILLS, if (r) 30 else 45),
            c.gym(DayOfWeek.MONDAY, "Push", GYM_BASE),
            c.run(
                DayOfWeek.TUESDAY,
                if (r) "Carrera · rodaje suave" else "Carrera · rodaje + progresivos",
                if (r) {
                    "30 min a ritmo conversacional."
                } else {
                    "40 min a ritmo conversacional (~5:45-6:00/km) y 4-6 progresivos de 20 s al final. En Base no " +
                        "hay series."
                },
                if (r) 30 else 40,
            ),
            c.bike(DayOfWeek.WEDNESDAY, "Bici · aeróbico", "Z2 a cadencia ágil, sin forzar.", if (r) 45 else 60),
            c.swim(
                DayOfWeek.THURSDAY,
                "Natación · fondo",
                if (r) {
                    "Nado suave y relajado, sin series."
                } else {
                    "Aplica la técnica del lunes: un bloque de " +
                        "$continuousM m continuos."
                },
                if (r) 30 else 45,
            ),
            c.gym(DayOfWeek.THURSDAY, "Pull", GYM_BASE),
            c.bike(
                DayOfWeek.SATURDAY,
                "Bici · larga",
                "Salida de resistencia en Z2.",
                if (r) 75 else min(150, 120 + 5 * c.step),
                if (r) Intensity.EASY else Intensity.LONG,
            ),
            c.longRun(longKm, 6.0),
        )
    }

    private fun baseLongKm(step: Int): Double = min(10.0, roundHalf(5.0 * 1.1.pow(step - 1)))

    // ---------------------------------------------------------------- Winter: the engine

    private fun winterWeek(c: WeekContext): List<PlannedSession> {
        if (c.recovery) return recoveryWeek(c, roundHalf(winterLongKm(c.step) * 0.7), rotationRoutine(c, slot = 0))
        val sweetSpot =
            when {
                c.step <= 4 -> "3×10 min"
                c.step <= 8 -> "3×12 min"
                else -> "2×20 min"
            }
        val runQuality =
            if (c.step % 2 == 1) {
                "Intervalos: 15 min suaves + 5×3 min fuertes (rec. 2 min trotando) + 10 min suaves."
            } else {
                "Tempo: 15 min suaves + ${min(25, 15 + c.step)} min a ~5:10-5:20/km + 10 min suaves."
            }
        return listOf(
            c.swim(
                DayOfWeek.MONDAY,
                "Natación · técnica + fondo",
                "Drills de crol y ${roundTo(min(1500, 1000 + 50 * c.step), 100)} m continuos.",
                50,
            ),
            c.gym(DayOfWeek.MONDAY, rotationRoutine(c, slot = 0), GYM_WINTER),
            c.run(DayOfWeek.TUESDAY, "Carrera · calidad", runQuality, 50, Intensity.KEY),
            c.bike(
                DayOfWeek.WEDNESDAY,
                "Rodillo · sweet spot",
                "ROUVY: $sweetSpot al 88-93 % del FTP con 5 min suaves entre bloques.",
                if (c.step <= 8) 60 else 75,
                Intensity.KEY,
            ),
            c.swim(
                DayOfWeek.THURSDAY,
                "Natación · umbral",
                "${min(12, 8 + c.step / 4)}×100 m con 15 s de descanso; cronometra y baja hacia 1:45/100 m.",
                50,
                Intensity.MODERATE,
            ),
            c.gym(DayOfWeek.THURSDAY, rotationRoutine(c, slot = 1), GYM_WINTER),
            c.bike(
                DayOfWeek.SATURDAY,
                "Bici · larga",
                "Z2 continuo (si hace mal tiempo, 90 min de rodillo bastan).",
                min(180, 120 + 5 * c.step),
                Intensity.LONG,
            ),
            c.longRun(winterLongKm(c.step), 5.9),
        )
    }

    private fun winterLongKm(step: Int): Double = min(14.0, 10.0 + 0.5 * step)

    /** Winter rotates the full Push → Pull → Leg cycle across its two weekly gym slots. */
    private fun rotationRoutine(
        c: WeekContext,
        slot: Int,
    ): String = gymRotation[((c.weekInPhase - 1) * 2 + slot) % gymRotation.size]

    // ---------------------------------------------------------------- Specific: race-shaped work

    private fun specificWeek(c: WeekContext): List<PlannedSession> {
        if (c.recovery) return recoveryWeek(c, roundHalf(specificLongKm(c.step) * 0.7), specificRoutine(c))
        val runReps =
            when {
                c.step <= 3 -> "3×2 km"
                c.step <= 6 -> "3×3 km"
                else -> "2×5 km"
            }
        val continuousM = roundTo(min(1800, 1200 + 50 * c.step), 100)
        return listOf(
            c.swim(
                DayOfWeek.MONDAY,
                "Natación · ritmo de carrera",
                "${min(7, 4 + c.step / 3)}×200 m a ritmo objetivo (~1:45/100 m) con 30 s de descanso.",
                50,
                Intensity.MODERATE,
            ),
            c.run(
                DayOfWeek.TUESDAY,
                "Carrera · ritmo objetivo",
                "15 min suaves + $runReps a 5:15-5:30/km (rec. 2 min) + 10 min suaves.",
                55,
                Intensity.KEY,
            ),
            c.bike(
                DayOfWeek.WEDNESDAY,
                "Bici · bloques a ritmo de carrera",
                "3×12 min a ritmo de prueba (~33-34 km/h en llano) con 4 min suaves.",
                75,
                Intensity.KEY,
            ),
            thursdaySwim(c, continuousM),
            c.gym(DayOfWeek.THURSDAY, specificRoutine(c), GYM_MAINTENANCE),
            c.bike(
                DayOfWeek.SATURDAY,
                "Ladrillo · bici",
                "Los últimos 20 min a ritmo de carrera; ten preparada la transición.",
                min(90, 60 + 5 * c.step),
                Intensity.LONG,
            ),
            c.run(
                DayOfWeek.SATURDAY,
                "Ladrillo · carrera",
                "Sin pausa al bajar de la bici: los primeros 10 min a ritmo objetivo, el resto suave.",
                min(30, 15 + 2 * c.step),
                Intensity.KEY,
            ),
            c.longRun(specificLongKm(c.step), 5.9),
        )
    }

    /** Upper-body only from here on: the legs are saved for the bricks. */
    private fun specificRoutine(c: WeekContext): String = if (c.weekInPhase % 2 == 1) "Push" else "Pull"

    private fun specificLongKm(step: Int): Double = min(15.0, 12.0 + 0.5 * step)

    /** From May the lake is swimmable: Thursday's swim moves to open water. */
    private fun thursdaySwim(
        c: WeekContext,
        meters: Int,
    ): PlannedSession =
        if (c.monday.monthValue >= 5) {
            c.swim(
                DayOfWeek.THURSDAY,
                "Natación · aguas abiertas",
                "Lago con neopreno: sighting cada 6-8 brazadas, salidas en grupo y $meters m continuos.",
                50,
            )
        } else {
            c.swim(DayOfWeek.THURSDAY, "Natación · fondo", "$meters m continuos a ritmo cómodo.", 50)
        }

    // ---------------------------------------------------------------- Recovery weeks (winter/specific)

    private fun recoveryWeek(
        c: WeekContext,
        longKm: Double,
        gymRoutine: String,
    ): List<PlannedSession> =
        listOf(
            c.swim(DayOfWeek.MONDAY, "Natación · técnica", TECHNIQUE_DRILLS, 30),
            c.gym(DayOfWeek.MONDAY, gymRoutine, GYM_RECOVERY),
            c.run(DayOfWeek.TUESDAY, "Carrera · rodaje suave", "30 min a ritmo conversacional.", 30),
            c.bike(DayOfWeek.WEDNESDAY, "Bici · suave", "Rodillo o calle en Z1-Z2, piernas sueltas.", 45),
            c.swim(DayOfWeek.THURSDAY, "Natación · suave", "Nado relajado, sin series.", 30),
            c.bike(DayOfWeek.SATURDAY, "Bici · Z2", "Salida tranquila, sin bloques.", 90),
            c.longRun(longKm, 6.0),
        )

    // ---------------------------------------------------------------- The last four weeks

    private fun sharpenWeek(c: WeekContext): List<PlannedSession> =
        listOf(
            c.swim(
                DayOfWeek.MONDAY,
                "Natación · ritmo de carrera",
                "6×200 m a ritmo objetivo, 30 s de descanso.",
                50,
            ),
            c.run(
                DayOfWeek.TUESDAY,
                "Carrera · ritmo objetivo",
                "15 min suaves + 2×4 km a 5:15-5:25/km (rec. 3 min) + 10 min suaves.",
                55,
                Intensity.KEY,
            ),
            c.bike(
                DayOfWeek.WEDNESDAY,
                "Bici · bloques",
                "3×12 min a ritmo de prueba con 4 min suaves.",
                75,
                Intensity.KEY,
            ),
            thursdaySwim(c, 1500),
            c.gym(DayOfWeek.THURSDAY, "Pull", GYM_LAST),
            c.bike(
                DayOfWeek.SATURDAY,
                "Ladrillo · bici",
                "Los últimos 30 min a ritmo de carrera.",
                90,
                Intensity.LONG,
            ),
            c.run(
                DayOfWeek.SATURDAY,
                "Ladrillo · carrera",
                "Directo de la bici: 20 min a ritmo objetivo.",
                30,
                Intensity.KEY,
            ),
            c.longRun(12.0, 5.9),
        )

    private fun simulationWeek(c: WeekContext): List<PlannedSession> =
        listOf(
            c.swim(DayOfWeek.MONDAY, "Natación · suave", "Técnica y 4×50 m vivos.", 40),
            c.run(
                DayOfWeek.TUESDAY,
                "Carrera · chispa",
                "15 min suaves + 4×1 km a 5:10/km (rec. 2 min).",
                45,
                Intensity.KEY,
            ),
            c.bike(DayOfWeek.WEDNESDAY, "Bici · bloques", "3×8 min a ritmo de prueba.", 60, Intensity.MODERATE),
            c.swim(DayOfWeek.THURSDAY, "Natación · aguas abiertas", "1500 m con neopreno simulando la salida.", 45),
            c.swim(
                DayOfWeek.SATURDAY,
                "Simulacro · natación 1,5 km",
                "Ritmo de prueba. Ensaya material, neopreno y T1.",
                30,
                Intensity.KEY,
            ),
            c.bike(
                DayOfWeek.SATURDAY,
                "Simulacro · bici 40 km",
                "Ritmo de prueba (~33-34 km/h) con la nutrición de carrera, tal cual la harás en Zúrich.",
                75,
                Intensity.KEY,
            ),
            c.run(
                DayOfWeek.SATURDAY,
                "Simulacro · carrera 10 km",
                "T2 rápida y 10 km a ~5:20/km. Objetivo del simulacro: por debajo de 2h35.",
                55,
                Intensity.KEY,
            ),
        )

    private fun taperWeek(c: WeekContext): List<PlannedSession> =
        listOf(
            c.swim(DayOfWeek.MONDAY, "Natación · suave", "Técnica y 4×50 m rápidos.", 30),
            c.run(
                DayOfWeek.TUESDAY,
                "Carrera · toque de ritmo",
                "20 min suaves + 4×2 min a ritmo de carrera.",
                35,
                Intensity.MODERATE,
            ),
            c.bike(
                DayOfWeek.WEDNESDAY,
                "Bici · toque de ritmo",
                "3×5 min a ritmo de prueba, el resto suave.",
                50,
                Intensity.MODERATE,
            ),
            c.swim(DayOfWeek.THURSDAY, "Natación · aguas abiertas", "Suave, practicando sighting y salidas.", 30),
            c.bike(DayOfWeek.SATURDAY, "Ladrillo corto · bici", "Z2 con 2×5 min a ritmo.", 60),
            c.run(DayOfWeek.SATURDAY, "Ladrillo corto · carrera", "15 min suaves al bajar de la bici.", 15),
            c.longRun(8.0, 5.9, intensity = Intensity.EASY),
        )

    private fun raceWeek(c: WeekContext): List<PlannedSession> =
        listOf(
            c.run(DayOfWeek.TUESDAY, "Carrera · activación", "20 min suaves + 4×1 min a ritmo de carrera.", 30),
            c.bike(DayOfWeek.WEDNESDAY, "Bici · activación", "35 min suaves + 3×3 min a ritmo.", 45),
            c.swim(DayOfWeek.THURSDAY, "Natación · aguas abiertas", "20 min muy suaves para sentir el neopreno.", 20),
            c.bike(DayOfWeek.SATURDAY, "Activación · bici", "20 min suaves revisando la bici montada.", 20),
            c.run(DayOfWeek.SATURDAY, "Activación · carrera", "10 min suaves con 3 cambios de ritmo cortos.", 10),
            PlannedSession(
                date = c.day(DayOfWeek.SUNDAY),
                sport = Sport.RACE,
                title = TriathlonPlan.RACE_NAME,
                detail =
                    "1,5 km + 40 km + 10 km. Sal conservador en el agua, come y bebe en la bici como ensayaste, " +
                        "y corre los primeros 2 km contenido.",
                minutes = 150,
                intensity = Intensity.RACE,
            ),
        )

    // ---------------------------------------------------------------- Builders

    private fun WeekContext.swim(
        dow: DayOfWeek,
        title: String,
        detail: String,
        minutes: Int,
        intensity: Intensity = Intensity.EASY,
    ) = PlannedSession(day(dow), Sport.SWIM, title, detail, minutes, intensity)

    private fun WeekContext.bike(
        dow: DayOfWeek,
        title: String,
        detail: String,
        minutes: Int,
        intensity: Intensity = Intensity.EASY,
    ) = PlannedSession(day(dow), Sport.BIKE, title, detail, minutes, intensity)

    private fun WeekContext.run(
        dow: DayOfWeek,
        title: String,
        detail: String,
        minutes: Int,
        intensity: Intensity = Intensity.EASY,
    ) = PlannedSession(day(dow), Sport.RUN, title, detail, minutes, intensity)

    private fun WeekContext.longRun(
        km: Double,
        minPerKm: Double,
        intensity: Intensity = if (recovery) Intensity.EASY else Intensity.LONG,
    ) = PlannedSession(
        date = day(DayOfWeek.SUNDAY),
        sport = Sport.RUN,
        title = "Carrera · larga",
        detail = "${formatKm(km)} km a ritmo conversacional; el 80 % fácil es lo que te hace rápido.",
        minutes = (km * minPerKm).roundToInt(),
        intensity = intensity,
        distanceKm = km,
    )

    private fun WeekContext.gym(
        dow: DayOfWeek,
        routine: String,
        intent: String,
    ) = PlannedSession(
        date = day(dow),
        sport = Sport.GYM,
        title = "Gym · ${ROUTINE_LABELS.getValue(routine)}",
        detail = "3×6-8 con 90 s de descanso. ${if (recovery) GYM_RECOVERY else intent}",
        minutes = 50,
        intensity = Intensity.MODERATE,
        gymRoutine = routine,
    )

    private fun roundHalf(km: Double): Double = (km * 2).roundToInt() / 2.0

    private fun roundTo(
        value: Int,
        step: Int,
    ): Int = (value.toDouble() / step).roundToInt() * step

    private fun formatKm(km: Double): String {
        val text = if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()
        return text.replace('.', ',')
    }

    private val ROUTINE_LABELS = mapOf("Push" to "Push", "Pull" to "Pull", "Leg" to "Pierna")

    private const val TECHNIQUE_DRILLS =
        "Drills de crol (catch, rolido, respiración bilateral) en series de 25-50 m con descanso; calidad sobre " +
            "cantidad."
    private const val GYM_BASE = "Progresión conservadora: +2,5 kg solo tras superar las reps objetivo con margen."
    private const val GYM_WINTER = "Fase de fuerza: es el momento de progresar cargas, sin llegar al fallo."
    private const val GYM_MAINTENANCE =
        "Mantenimiento: mismas cargas, sin buscar progresión; las piernas son para " +
            "los ladrillos."
    private const val GYM_RECOVERY = "Semana de descarga: mismas cargas, sin progresar."
    private const val GYM_LAST = "Última sesión antes de la carrera: ligera, mismas cargas."

    private const val SHARPEN_FOCUS = "Puesta a punto · último bloque de calidad a ritmo de carrera."
    private const val SIMULATION_FOCUS =
        "Puesta a punto · simulacro completo el sábado: ritmos, transiciones y la nutrición de carrera."
    private const val TAPER_FOCUS = "Puesta a punto · volumen −40 % con toques cortos de intensidad. Sin gym."
    private const val RACE_FOCUS = "Semana de la prueba: volumen mínimo, dormir bien, material y logística listos."
}
