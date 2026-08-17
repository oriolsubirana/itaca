package cat.subi.itaca.nutrition.domain

/** One food category of the phase-1 diet: what to eat, how often, and what to prioritize. */
data class FoodRule(
    val category: String,
    val guidance: String,
    val items: String,
)

/** One time slot of the supplement protocol. */
data class SupplementSlot(
    val moment: String,
    val items: List<String>,
)

/**
 * "Plan Paleomind" — the professional diet + supplementation plan for Oriol's ulcerative colitis
 * (phase 1), authored by his integrative dietitian (David Ruiz, Paleomind). Static reference data
 * transcribed from the plan document; user-facing strings are Spanish by convention.
 *
 * It complements — never replaces — medical advice: Ítaca describes the plan and never prescribes.
 */
object DietPlan {
    const val SOURCE = "Plan Paleomind (David Ruiz, dietista integrativo) · Fase 1 · colitis ulcerosa"
    const val PHASE =
        "Fase 1 de 3 (1-2 meses): subir niveles nutricionales, mejorar síntomas digestivos y preparar " +
            "la fase 2 (remisión completa de síntomas y calprotectina). La fase 3 aborda causas (microbiota)."

    val allowed =
        listOf(
            FoodRule(
                "Pescado azul (mín. 4 días/sem)",
                "Fresco o congelado mejor; en lata, en AOVE o al natural",
                "Priorizar anchoa, sardina, boquerón, salmón, salmonete y caballa; menos interesantes " +
                    "arenque, atún, bonito, melva y trucha",
            ),
            FoodRule(
                "Carnes (roja de pasto 2/sem, blanca 1/sem)",
                "La roja aporta más minerales; priorizar eco o de pasto. Evitar conejo y liebre",
                "Ternera, lechal, vacuno, buey, cerdo y cordero; blancas: pollo, pavo, faisán, pato",
            ),
            FoodRule(
                "Pescado blanco (2/sem)",
                "",
                "Rape, dorada, lubina y gallo; menos interesantes besugo y mero",
            ),
            FoodRule(
                "Vegetales (a diario, poca cantidad al inicio)",
                "Probar tolerancia y subir poco a poco",
                "Acelga, lechuga, endivia, judía verde, calabacín, calabaza, zanahoria, apio, champiñón y " +
                    "seta, tomate, pimiento; brócoli, ajo y cebolla a probar tolerancia",
            ),
            FoodRule(
                "Frutas (varias raciones/día, probar tolerancia)",
                "",
                "Arándanos, mora, manzana, fresa, frambuesa, granada, aguacate, papaya, aceituna, coco, " +
                    "banana y plátano macho",
            ),
            FoodRule(
                "Huevos (a diario)",
                "Preferible ecológicos (código 0) o camperos (1); cocidos, revueltos o escalfados",
                "Huevos",
            ),
            FoodRule(
                "Lácteos de cabra u oveja (un poco cada día)",
                "La lactosa no es mala si se tolera; sin azúcares ni edulcorantes añadidos",
                "Leche entera, quesos y quesos frescos, yogur, mantequilla y ghee",
            ),
            FoodRule(
                "Tubérculos (probar tolerancia)",
                "Cocidos o al horno; mejor enfriados (almidón resistente), máx. 100 g/día de enfriados",
                "Patata, batata y boniato",
            ),
            FoodRule(
                "Frutos secos y semillas",
                "Solo pipas de calabaza en esta fase, un poco varias veces/sem",
                "Pipas de calabaza",
            ),
            FoodRule(
                "Crustáceos y bivalvos (2/sem)",
                "Fuente potente de minerales",
                "Mejillón, almeja, ostra, gamba, gambón, buey de mar, pulpo y sepia",
            ),
            FoodRule(
                "Otros",
                "AOVE en crudo; embutido máx. 1/sem y sin aditivos (jamón, chorizo, lomo); harina solo " +
                    "de coco; stevia mínima como único edulcorante",
                "Chocolate ≥90 % o cacao puro, hígado de ternera (1/sem), caldo de huesos casero (2/sem), " +
                    "gelatina neutra, vinagre de manzana filtrado",
            ),
            FoodRule(
                "Bebidas",
                "Café según tolerancia; bebida vegetal solo de coco o almendra sin aditivos",
                "Agua (con gas vale), agua de mar diluida, té e infusiones (manzanilla, matcha)",
            ),
            FoodRule(
                "Especias (probar tolerancia)",
                "Esenciales para el intestino",
                "Orégano, romero, cúrcuma, jengibre, canela, comino, azafrán, anís, hinojo, menta, " +
                    "hierbabuena, nuez moscada, pimientas, salvia y albahaca",
            ),
        )

    val avoid =
        listOf(
            "Ultraprocesados, azúcares y edulcorantes (salvo stevia mínima)",
            "Cereales, pasta y pan — huir del gluten (avena, trigo, cebada, centeno, maíz, espelta, kamut, quinoa)",
            "Legumbres (soja, cacahuete, garbanzo, lenteja, habas, judías) — se verán en fases posteriores",
            "Frutas fuera del plan: plátano, melocotón, melón, naranja, piña, pera, níspero, uva…",
            "Frutos secos salvo pipas de calabaza (anacardo, pistacho, avellana, nuez, almendra…)",
            "Aceites de semillas (girasol, colza) y margarina",
            "Bebidas vegetales de soja o avena",
            "Alcohol y refrescos",
            "Fermentados en esta fase (kéfir, kombucha, verduras fermentadas): podrían dar síntomas",
        )

    val mealHabits =
        listOf(
            "Comer relajado y sin pantallas: el estrés apaga la digestión",
            "Masticar bien cada bocado",
            "Beber poca agua en las comidas; mejor un buen vaso ~30 min antes",
            "Caminar 15 min después de cada comida",
            "Ayuno intermitente progresivo: retrasar la primera ingesta 1 h un par de días/sem → 12:12 → " +
                "14:10 con el tiempo; nada de 16:8 hasta llevar meses. Mejor no ayunar que ayunar con ansiedad",
        )

    val supplementSchedule =
        listOf(
            SupplementSlot(
                "Por la mañana en ayunas",
                listOf(
                    "2 vasos de agua de mar",
                    "Glutamina 5 g",
                    "Selenio 200 mcg",
                    "NAC 600 mg (mejor fuera de comidas)",
                ),
            ),
            SupplementSlot(
                "Antes del almuerzo",
                listOf(
                    "1 cápsula de vitamina B",
                    "1 cucharada de vinagre de manzana disuelta en agua (si genera ardor, reducir o quitar)",
                ),
            ),
            SupplementSlot(
                "Con la comida",
                listOf(
                    "Vitamina D3+K2: 10.000 UI el primer mes, 5.000 UI después (con grasas)",
                    "Omega-3 EPA 1 g",
                    "Glutamina 5 g",
                    "1 cápsula de molibdeno",
                ),
            ),
            SupplementSlot(
                "Media hora antes de dormir",
                listOf(
                    "2 cápsulas de triple magnesio",
                    "Zinc 12 mg",
                    "NAC 600 mg",
                ),
            ),
        )

    val keyHabits =
        listOf(
            "Rutina matinal: raspador lingual y oil pulling con aceite de coco, agua de mar sin prisas, " +
                "3 respiraciones (5-20-10 s), ducha fría progresiva y paseo de 10-15 min",
            "Descanso: horario regular, habitación oscura y fresca (17-19 ºC), luz solar por la mañana, " +
                "sin pantallas antes de dormir",
            "Gestión del estrés: mindfulness o estoicismo; 3 meditaciones de 10 min al día",
            "Exposición al frío progresiva y grounding (caminar descalzo en hierba, tierra o arena)",
            "Higiene bucal: pasta sin flúor, hilo o irrigador, raspador lingual y enjuague casero " +
                "(aceite de coco o agua con sal, 2-3 min/día)",
            "Deporte: fuerza como base, cardio como complemento, y ~10.000 pasos diarios",
        )

    const val DISCLAIMER =
        "Plan personalizado de su dietista; complementa y no sustituye la consulta médica ni la " +
            "medicación pautada. Cualquier duda clínica o cambio, con su médico/gastroenterólogo."
}
