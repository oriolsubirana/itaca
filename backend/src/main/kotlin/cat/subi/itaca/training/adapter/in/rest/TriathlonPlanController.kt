@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.training.adapter.`in`.rest

import cat.subi.itaca.training.application.TriathlonPlanService
import cat.subi.itaca.training.application.TriathlonPlanView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class TriathlonPlanController(
    private val plan: TriathlonPlanService,
) {
    @GetMapping("/api/training/plan")
    fun plan(): TriathlonPlanView = plan.view(LocalDate.now())
}
