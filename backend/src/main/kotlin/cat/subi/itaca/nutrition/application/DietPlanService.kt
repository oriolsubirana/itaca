package cat.subi.itaca.nutrition.application

import cat.subi.itaca.nutrition.domain.DietPlan
import cat.subi.itaca.nutrition.domain.FoodRule
import cat.subi.itaca.nutrition.domain.SupplementSlot
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service

data class DietPlanView(
    val source: String,
    val phase: String,
    val allowed: List<FoodRule>,
    val avoid: List<String>,
    val mealHabits: List<String>,
    val supplementSchedule: List<SupplementSlot>,
    val keyHabits: List<String>,
    val disclaimer: String,
)

/** The professional diet + supplementation plan as a chat tool. */
@Service
class DietPlanService : ChatTools {
    @Tool(
        name = "query_diet_plan",
        description =
            "Oriol's professional diet + supplementation plan (Paleomind, phase 1 for ulcerative " +
                "colitis): allowed foods with frequencies, foods to avoid, meal/digestion habits, the " +
                "supplement schedule by time of day, and key lifestyle habits. Use it for any question " +
                "about what to eat, meal ideas, whether a food fits the plan, or which supplements are " +
                "due — and to judge onPlan when logging meals.",
    )
    fun queryPlan(): DietPlanView =
        DietPlanView(
            source = DietPlan.SOURCE,
            phase = DietPlan.PHASE,
            allowed = DietPlan.allowed,
            avoid = DietPlan.avoid,
            mealHabits = DietPlan.mealHabits,
            supplementSchedule = DietPlan.supplementSchedule,
            keyHabits = DietPlan.keyHabits,
            disclaimer = DietPlan.DISCLAIMER,
        )
}
