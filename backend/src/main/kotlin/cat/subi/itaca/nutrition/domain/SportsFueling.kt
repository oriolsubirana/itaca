// The g/kg ranges below are the sports-nutrition reference values themselves (domain data).
@file:Suppress("MagicNumber")

package cat.subi.itaca.nutrition.domain

/**
 * How demanding the training day is. Same wire codes as the training plan's day load: the edge
 * (frontend, chat) reads the day type from the training context and the fueling from here, so
 * neither context depends on the other.
 */
enum class TrainingDayType {
    REST,
    EASY,
    KEY,
    LONG,
    RACE,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): TrainingDayType? = entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}

data class MacroGrams(
    val carbsMin: Int,
    val carbsMax: Int,
    val proteinMin: Int,
    val proteinMax: Int,
)

data class DayFuelingTarget(
    val dayType: TrainingDayType,
    val label: String,
    val carbsGPerKgMin: Double,
    val carbsGPerKgMax: Double,
    val proteinGPerKgMin: Double,
    val proteinGPerKgMax: Double,
    val guidance: String,
) {
    fun gramsFor(weightKg: Double) =
        MacroGrams(
            carbsMin = (carbsGPerKgMin * weightKg).toInt(),
            carbsMax = (carbsGPerKgMax * weightKg).toInt(),
            proteinMin = (proteinGPerKgMin * weightKg).toInt(),
            proteinMax = (proteinGPerKgMax * weightKg).toInt(),
        )
}

data class SessionFueling(
    val fromMinutes: Int,
    val label: String,
    val before: String,
    val during: String,
    val after: String,
)

data class FuelingStep(
    val moment: String,
    val guidance: List<String>,
)

/**
 * Sports fueling for the triathlon build, layered on top of the Paleomind diet: the carbohydrate
 * sources are the ones the plan allows (tubers, squash, banana/plantain, plan fruit). Orientation,
 * not prescription — anything that conflicts with the dietitian's plan is flagged to agree with him.
 * User-facing strings are Spanish.
 */
object SportsFueling {
    const val CARB_SOURCES =
        "Fuentes del plan Paleomind: boniato, patata (mejor enfriada), calabaza, plátano macho, banana y la fruta " +
            "permitida."

    val dayTargets =
        listOf(
            DayFuelingTarget(
                TrainingDayType.REST,
                "Descanso",
                2.0,
                3.0,
                1.6,
                1.8,
                "Más verdura y grasa buena (AOVE, pescado azul, aguacate); el carbohidrato justo. Buen día para el " +
                    "hígado o el caldo de huesos.",
            ),
            DayFuelingTarget(
                TrainingDayType.EASY,
                "Suave",
                3.0,
                4.0,
                1.6,
                1.8,
                "Como un día normal del plan con una ración de tubérculo en la comida más cercana al entreno.",
            ),
            DayFuelingTarget(
                TrainingDayType.KEY,
                "Sesión clave",
                4.5,
                6.0,
                1.7,
                2.0,
                "Llega con el depósito lleno: tubérculo o plátano macho " +
                    "en la comida previa y en la de después de la sesión.",
            ),
            DayFuelingTarget(
                TrainingDayType.LONG,
                "Tirada larga",
                6.0,
                7.0,
                1.7,
                2.0,
                "Carbohidrato en todas las comidas y la víspera también. Sin cereales es difícil llegar: si no " +
                    "alcanzas, coméntalo con tu dietista (quizá arroz blanco en fases posteriores).",
            ),
            DayFuelingTarget(
                TrainingDayType.RACE,
                "Carrera / carga",
                7.0,
                8.0,
                1.5,
                1.8,
                "Solo los 1-2 días previos a la prueba: carga de carbohidrato con alimentos conocidos y bajos en " +
                    "residuo.",
            ),
        )

    val sessionFueling =
        listOf(
            SessionFueling(
                0,
                "Menos de 60 min",
                "Nada especial; si entrenas en ayunas, que sea suave.",
                "Agua.",
                "La siguiente comida normal, con proteína.",
            ),
            SessionFueling(
                60,
                "60-90 min",
                "Comida con carbohidrato 2-3 h antes, baja en fibra y grasa.",
                "Agua con electrolitos (el agua de mar diluida del plan sirve), sobre todo con calor.",
                "En 1-2 h: proteína (~0,3 g/kg) + carbohidrato del plan.",
            ),
            SessionFueling(
                90,
                "Más de 90 min",
                "Comida con carbohidrato 3 h antes; un plátano 30-45 min antes.",
                "30-60 g de carbohidrato por hora desde el minuto 45 + electrolitos. " +
                    "El plan evita azúcares: acuerda con tu dietista la opción " +
                    "(banana, puré de boniato, un gel sin aditivos) y ensáyala aquí, nunca el día de la prueba.",
                "En 1 h: proteína (~0,3 g/kg) + carbohidrato generoso; rehidrata con sal.",
            ),
        )

    val raceWeek =
        listOf(
            FuelingStep(
                "D-7 a D-3",
                listOf(
                    "Plan Paleomind normal; el volumen baja, así que no hace falta comer más.",
                    "Nada nuevo en el plato: solo alimentos que ya toleras bien.",
                ),
            ),
            FuelingStep(
                "D-2 y D-1 · carga",
                listOf(
                    "Carbohidrato alto (7-8 g/kg/día) repartido en todas las comidas: boniato, patata, plátano " +
                        "macho, banana.",
                    "Dieta baja en residuo 48 h antes (clave con la colitis): verdura cocida y pelada, sin crudos ni " +
                        "exceso de fibra.",
                    "Menos grasa y fibra para dejar sitio al carbohidrato; hidratación con sal.",
                ),
            ),
            FuelingStep(
                "Cena de la víspera",
                listOf(
                    "Pronto (2-3 h antes de dormir), sencilla y conocida: pescado blanco o huevo + tubérculo.",
                ),
            ),
            FuelingStep(
                "Mañana de la carrera",
                listOf(
                    "Desayuno 3 h antes de la salida: 1-2 g/kg de carbohidrato que ya hayas ensayado (p. ej. boniato " +
                        "o plátano + huevo).",
                    "Café solo si lo toleras y lo has probado en entreno.",
                    "30 min antes: un plátano o lo que hayas ensayado, y unos sorbos de agua.",
                ),
            ),
            FuelingStep(
                "Durante la prueba",
                listOf(
                    "Bici: el plan de 30-60 g/h ensayado en las tiradas largas y el simulacro; bebe a sorbos.",
                    "Carrera: agua o electrolitos en los avituallamientos; nada que no hayas probado.",
                ),
            ),
            FuelingStep(
                "Después",
                listOf(
                    "Comida de recuperación con proteína y carbohidrato, y vuelta al plan al día siguiente.",
                ),
            ),
        )

    val principles =
        listOf(
            "Come para el entreno de hoy y de mañana: el carbohidrato sube y baja con la sesión, la proteína es " +
                "constante.",
            "Nada nuevo el día de la prueba: todo lo de carrera se ensaya en las tiradas largas y el simulacro.",
            "En brote, manda la colitis: comida suave y de bajo residuo, y el entreno se adapta (consulta con tu " +
                "gastroenterólogo).",
            "Composición al servicio del rendimiento: déficit pequeño solo en días suaves o de descanso, " +
                "nunca antes de una sesión clave.",
        )

    const val DISCLAIMER =
        "Orientación de nutrición deportiva sobre el plan de tu dietista; no lo sustituye. Cambios de dieta o " +
            "suplementación, " +
            "con él; dudas clínicas por la colitis, con tu gastroenterólogo."

    fun targetFor(dayType: TrainingDayType): DayFuelingTarget = dayTargets.single { it.dayType == dayType }
}
