// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.finance.adapter.`in`.rest

import cat.subi.itaca.finance.application.FinanceAccountService
import cat.subi.itaca.finance.application.FinanceAiCategorizationService
import cat.subi.itaca.finance.application.FinanceImportService
import cat.subi.itaca.finance.application.FinanceOverview
import cat.subi.itaca.finance.application.FinanceQueries
import cat.subi.itaca.finance.application.ImportResult
import cat.subi.itaca.finance.application.MonthView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

data class SetBalanceRequest(
    val accountId: Long,
    val balance: BigDecimal,
)

data class SetCategoryRequest(
    val transactionId: Long,
    val category: String,
)

/** Finance dashboard: months/accounts overview, a month breakdown, statement import, balances. */
@RestController
@RequestMapping("/api/finance")
class FinanceController(
    private val queries: FinanceQueries,
    private val importService: FinanceImportService,
    private val accountService: FinanceAccountService,
    private val aiCategorization: FinanceAiCategorizationService,
) {
    @GetMapping("/overview")
    fun overview(): FinanceOverview = queries.overview()

    @GetMapping("/month")
    fun month(
        @RequestParam month: String,
        @RequestParam currency: String,
    ): MonthView = queries.month(month, currency)

    @PostMapping("/import")
    fun import(
        @RequestParam accountId: Long,
        @RequestPart("file") file: MultipartFile,
    ): ImportResult = importService.import(accountId, file.bytes)

    @PostMapping("/balance")
    fun setBalance(
        @RequestBody request: SetBalanceRequest,
    ): FinanceOverview {
        accountService.setBalance(request.accountId, request.balance)
        return queries.overview()
    }

    @PostMapping("/category")
    fun setCategory(
        @RequestBody request: SetCategoryRequest,
    ): Map<String, String> {
        accountService.setCategory(request.transactionId, request.category)
        return mapOf("category" to request.category)
    }

    @PostMapping("/categorize-ai")
    fun categorizeAi(): Map<String, Int> = mapOf("updated" to aiCategorization.categorizeOther())
}
