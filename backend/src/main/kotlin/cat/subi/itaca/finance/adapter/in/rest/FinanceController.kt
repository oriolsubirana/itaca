// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.finance.adapter.`in`.rest

import cat.subi.itaca.finance.application.FinanceOverview
import cat.subi.itaca.finance.application.FinanceQueries
import cat.subi.itaca.finance.application.MonthView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Read-only finance dashboard: the months/accounts overview and a month breakdown. */
@RestController
@RequestMapping("/api/finance")
class FinanceController(
    private val queries: FinanceQueries,
) {
    @GetMapping("/overview")
    fun overview(): FinanceOverview = queries.overview()

    @GetMapping("/month")
    fun month(
        @RequestParam month: String,
        @RequestParam currency: String,
    ): MonthView = queries.month(month, currency)
}
