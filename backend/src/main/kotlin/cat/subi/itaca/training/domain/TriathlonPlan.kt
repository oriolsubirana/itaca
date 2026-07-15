package cat.subi.itaca.training.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PlanPhase(
    val key: String,
    val name: String,
    val objective: String,
    val start: LocalDate,
    val end: LocalDate,
    val guidance: List<String>,
    val milestone: String,
) {
    val totalWeeks: Int = (ChronoUnit.DAYS.between(start, end).toInt() / DAYS_PER_WEEK) + 1

    fun weekOf(date: LocalDate): Int = (ChronoUnit.DAYS.between(start, date).toInt() / DAYS_PER_WEEK) + 1

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

data class TemplateDay(
    val day: String,
    val session: String,
    val focus: String,
)

data class RaceTarget(
    val sector: String,
    val target: String,
    val time: String,
    val comment: String,
)

/**
 * "Camino al sub-2h30": the Zurich Olympic triathlon training plan (user-provided document,
 * rescheduled to the 2027 edition). Static reference data — progress against it is computed
 * from the imported Strava activities. User-facing strings are Spanish by convention.
 */
object TriathlonPlan {
    val raceDate: LocalDate = LocalDate.parse("2027-06-27")
    const val RACE_NAME = "Triatlón Olímpico de Zúrich"
    const val GOAL = "Sub-2h30 · 1,5 km natación · 40 km bici · 10 km carrera"

    val phases =
        listOf(
            PlanPhase(
                key = "base",
                name = "Base",
                objective =
                    "Llegar a correr 10 km cómodo y arrancar la reconstrucción técnica de la natación. " +
                        "Sin intensidad alta: todo volumen fácil y técnica.",
                start = LocalDate.parse("2026-07-13"),
                end = LocalDate.parse("2026-10-31"),
                guidance =
                    listOf(
                        "Natación 2×/sem: prioridad absoluta a técnica (catch, rolido, respiración bilateral, " +
                            "posición). Series cortas de 25-50 m con descanso; calidad sobre cantidad.",
                        "Carrera: de 5 a 10 km progresivo, +10% al largo del domingo por semana, todo a ritmo " +
                            "conversacional (~5:45-6:00/km). Sin dolor, sin prisa.",
                        "Bici: mantener — una larga el finde y una entre semana. Aquí no hay que construir nada.",
                    ),
                milestone = "Fin de octubre: correr 10 km seguidos + nadar 1000 m continuos con técnica decente.",
            ),
            PlanPhase(
                key = "winter",
                name = "Invierno",
                objective =
                    "Fuerza aeróbica en interior: rodillo estructurado, piscina y la primera calidad en carrera. " +
                        "La fase de motor.",
                start = LocalDate.parse("2026-11-01"),
                end = LocalDate.parse("2027-02-28"),
                guidance =
                    listOf(
                        "Natación 2×/sem: series de umbral (p. ej. 8×100 m con descanso corto) y cronometrar " +
                            "los 100 m para medir progreso.",
                        "Carrera: 1 sesión de calidad/sem — intervalos (5×3 min fuertes) o tempo de 20 min — " +
                            "manteniendo la larga del finde.",
                        "Bici: rodillo sweet-spot/FTP (ROUVY) 2×/sem; 45-75 min estructurados rinden más " +
                            "que horas sueltas.",
                    ),
                milestone = "Fin de febrero: 10 km por debajo de ~52 min + 100 m nadando a ~1:45.",
            ),
            PlanPhase(
                key = "specific",
                name = "Específico",
                objective =
                    "Transformar forma en rendimiento de triatlón: ladrillos bici→carrera y aguas abiertas " +
                        "cuando el lago lo permita.",
                start = LocalDate.parse("2027-03-01"),
                end = LocalDate.parse("2027-05-31"),
                guidance =
                    listOf(
                        "Ladrillos 1×/sem: bici + carrera encadenadas (60 min bici → 20-30 min correr) para " +
                            "aprender a correr con las piernas cargadas.",
                        "Aguas abiertas (desde mayo) 1×/sem: sighting, neopreno y salidas — muy distinto a la piscina.",
                        "Carrera: sesiones al ritmo objetivo de la prueba (~5:15-5:30/km).",
                    ),
                milestone = "Fin de mayo: simulacro (casi) completo — 1,5 km + 40 km + 10 km — por debajo de 2h35.",
            ),
            PlanPhase(
                key = "peak",
                name = "Puesta a punto",
                objective =
                    "Llegar fresco y afilado el 27 de junio: bajar volumen, mantener chispa y descansar. " +
                        "En junio no se gana forma; solo se pierde por fatiga o lesión.",
                start = LocalDate.parse("2027-06-01"),
                end = LocalDate.parse("2027-06-27"),
                guidance =
                    listOf(
                        "Semanas 1-2: último bloque de calidad + simulacro completo de competición (ritmos, " +
                            "nutrición, transiciones).",
                        "Semana 3: volumen −40%, algún toque corto de intensidad para no apagarse.",
                        "Semana de la prueba: volumen mínimo, sesiones cortas y suaves, dormir bien, material " +
                            "y logística listos con antelación.",
                    ),
                milestone = "27 de junio: sub-2h30 en Zúrich.",
            ),
        )

    val weeklyTemplate =
        listOf(
            TemplateDay("Lunes", "Natación (técnica) + Gym Push", "Drills de crol; gym por la tarde"),
            TemplateDay("Martes", "Carrera (calidad)", "Series o tempo según fase — la sesión clave de correr"),
            TemplateDay("Miércoles", "Bici (rodillo o calle)", "Mantenimiento aeróbico / sweet spot"),
            TemplateDay("Jueves", "Natación (fondo) + Gym Pull", "Volumen aplicando la técnica del lunes"),
            TemplateDay("Viernes", "Descanso o movilidad", "Pilates suave / estiramientos"),
            TemplateDay("Sábado", "Bici larga", "El punto fuerte: salida de resistencia"),
            TemplateDay("Domingo", "Carrera larga (progresiva)", "Construir hacia los 10 km"),
        )

    val raceTargets =
        listOf(
            RaceTarget("Natación 1,5 km", "~1:45 /100m", "~26-27 min", "Depende 100% de la técnica de estos meses"),
            RaceTarget("T1", "—", "~2 min", "Practicar: minutos gratis"),
            RaceTarget("Bici 40 km", "~33-34 km/h", "~1:11-1:13", "Tu terreno, cómodo para ti"),
            RaceTarget("T2", "—", "~1:30", "Practicar la transición bici-carrera"),
            RaceTarget("Carrera 10 km", "~5:20 /km", "~53 min", "El gran reto: sostenerlo tras la bici"),
            RaceTarget("TOTAL", "", "~2h27", "Objetivo cumplido con margen"),
        )

    val principles =
        listOf(
            "La natación es tu mayor palanca: cada minuto de técnica ahora vale por tres de fondo.",
            "Corre despacio para correr rápido: el 80% fácil. La lesión es el único enemigo real.",
            "La bici ya está ganada: mantener, no maximizar.",
            "El gym sirve, con medida: 2 días para prevenir lesiones; nada de hipertrofia máxima.",
            "Ensaya la prueba antes de la prueba: transiciones, nutrición, aguas abiertas, neopreno.",
        )

    fun phaseOn(date: LocalDate): PlanPhase? = phases.firstOrNull { date in it.start..it.end }

    fun daysToRace(date: LocalDate): Long = ChronoUnit.DAYS.between(date, raceDate)
}
