package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.persistence.DiaryEntryEntity
import cat.subi.itaca.health.adapter.out.persistence.DiaryEntryRepository
import cat.subi.itaca.health.adapter.out.persistence.FlareEntity
import cat.subi.itaca.health.adapter.out.persistence.FlareRepository
import cat.subi.itaca.health.domain.BristolScale
import cat.subi.itaca.health.domain.FlareSeverity
import cat.subi.itaca.health.domain.Score
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

data class DiaryEntryDto(
    val date: String,
    val bristol: Int?,
    val pain: Int?,
    val urgency: Int?,
    val blood: Boolean,
    val bowelMovements: Int?,
    val stress: Int?,
    val notes: String?,
)

data class DiaryEntryResult(
    val saved: Boolean,
    val entry: DiaryEntryDto? = null,
    val error: String? = null,
)

data class FlareDto(
    val id: Long,
    val startDate: String,
    val endDate: String?,
    val severity: String,
    val notes: String?,
)

data class FlareResult(
    val saved: Boolean,
    val flare: FlareDto? = null,
    val error: String? = null,
)

data class HealthSummary(
    val activeFlare: FlareDto?,
    val recentEntries: List<DiaryEntryDto>,
)

data class DiaryEntryUpdate(
    val date: LocalDate,
    val bristol: Int? = null,
    val pain: Int? = null,
    val urgency: Int? = null,
    val blood: Boolean? = null,
    val bowelMovements: Int? = null,
    val stress: Int? = null,
    val notes: String? = null,
)

/** Partial flare update; endDate accepts "" to reopen (clear the end). */
data class FlareUpdate(
    val startDate: String? = null,
    val endDate: String? = null,
    val severity: String? = null,
    val notes: String? = null,
)

/**
 * Application service of the health context, exposed to the chat as tools and
 * reused by the REST adapter. Records and retrieves data only — never
 * interprets it (see CLAUDE.md domain rules).
 */
@Service
class HealthTools(
    private val diary: DiaryEntryRepository,
    private val flares: FlareRepository,
) : ChatTools {
    @Tool(
        name = "log_diary_entry",
        description =
            "Logs or updates the IBD diary entry of a date (default today). Only the provided fields are " +
                "changed, so it can be called several times a day. bristol is 1-7; pain, urgency and stress " +
                "are 0-10; bowelMovements is the daily count.",
    )
    @Transactional
    fun logDiaryEntry(
        @ToolParam(description = "Date YYYY-MM-DD; omit for today", required = false) date: String?,
        @ToolParam(description = "Bristol scale 1-7", required = false) bristol: Int?,
        @ToolParam(description = "Pain 0-10", required = false) pain: Int?,
        @ToolParam(description = "Urgency 0-10", required = false) urgency: Int?,
        @ToolParam(description = "Blood present", required = false) blood: Boolean?,
        @ToolParam(description = "Number of bowel movements", required = false) bowelMovements: Int?,
        @ToolParam(description = "Stress 0-10", required = false) stress: Int?,
        @ToolParam(description = "Free-text notes", required = false) notes: String?,
    ): DiaryEntryResult =
        runCatching {
            val entry =
                upsert(
                    DiaryEntryUpdate(
                        date = date?.let(LocalDate::parse) ?: LocalDate.now(),
                        bristol = bristol,
                        pain = pain,
                        urgency = urgency,
                        blood = blood,
                        bowelMovements = bowelMovements,
                        stress = stress,
                        notes = notes,
                    ),
                )
            DiaryEntryResult(saved = true, entry = entry)
        }.getOrElse { DiaryEntryResult(saved = false, error = it.message) }

    @Tool(
        name = "log_flare",
        description =
            "Starts or ends an IBD flare. action is 'start' (requires severity: mild, moderate or severe) " +
                "or 'end' (closes the active flare). Date defaults to today.",
    )
    @Transactional
    fun logFlare(
        @ToolParam(description = "'start' or 'end'") action: String,
        @ToolParam(description = "Severity when starting: mild, moderate or severe", required = false)
        severity: String?,
        @ToolParam(description = "Date YYYY-MM-DD; omit for today", required = false) date: String?,
        @ToolParam(description = "Free-text notes", required = false) notes: String?,
    ): FlareResult =
        runCatching {
            val day = date?.let(LocalDate::parse) ?: LocalDate.now()
            when (action.trim().lowercase()) {
                "start" -> FlareResult(saved = true, flare = startFlare(day, severity, notes))
                "end" -> FlareResult(saved = true, flare = endFlare(day))
                else -> FlareResult(saved = false, error = "Unknown action: $action (use start or end)")
            }
        }.getOrElse { FlareResult(saved = false, error = it.message) }

    @Tool(
        name = "query_health",
        description = "Returns the active flare (if any) and the diary entries of the last days (default 30).",
    )
    fun queryHealth(
        @ToolParam(description = "How many days back to look (default 30)", required = false) days: Int?,
    ): HealthSummary =
        HealthSummary(
            activeFlare = flares.findFirstByEndDateIsNullOrderByStartDateDesc()?.toDto(),
            recentEntries =
                diary
                    .findByDateGreaterThanEqualOrderByDateDesc(
                        LocalDate.now().minusDays((days ?: DEFAULT_QUERY_DAYS).toLong()),
                    ).map { it.toDto() },
        )

    /** Shared upsert used by the chat tool and the REST form. */
    @Transactional
    fun upsert(update: DiaryEntryUpdate): DiaryEntryDto {
        update.bristol?.let { BristolScale.of(it) }
        update.pain?.let { Score.of(it) }
        update.urgency?.let { Score.of(it) }
        update.stress?.let { Score.of(it) }
        require((update.bowelMovements ?: 0) >= 0) { "bowelMovements cannot be negative" }

        val entity = diary.findByDate(update.date) ?: DiaryEntryEntity(date = update.date)
        update.bristol?.let { entity.bristol = it }
        update.pain?.let { entity.pain = it }
        update.urgency?.let { entity.urgency = it }
        update.blood?.let { entity.blood = it }
        update.bowelMovements?.let { entity.bowelMovements = it }
        update.stress?.let { entity.stress = it }
        update.notes?.takeIf { it.isNotBlank() }?.let { entity.notes = it }
        return diary.save(entity).toDto()
    }

    fun entryOf(date: LocalDate): DiaryEntryDto? = diary.findByDate(date)?.toDto()

    fun recentFlares(): List<FlareDto> = flares.findTop10ByOrderByStartDateDesc().map { it.toDto() }

    @Transactional
    fun deleteEntry(date: LocalDate) {
        val entity = diary.findByDate(date) ?: throw NoSuchElementException("No diary entry for $date")
        diary.delete(entity)
    }

    @Transactional
    fun updateFlare(
        id: Long,
        update: FlareUpdate,
    ): FlareDto {
        val flare = flares.findById(id).orElseThrow { NoSuchElementException("Flare $id not found") }
        // Validate the resulting state BEFORE mutating: the entity is managed and
        // a dirty invalid value would still be flushed despite the exception.
        val newStart = update.startDate?.let(LocalDate::parse) ?: flare.startDate
        val newEnd =
            when {
                update.endDate == null -> flare.endDate
                update.endDate.isBlank() -> null
                else -> LocalDate.parse(update.endDate)
            }
        newEnd?.let { require(!it.isBefore(newStart)) { "End date cannot be before the start date" } }
        flare.startDate = newStart
        flare.endDate = newEnd
        update.severity?.let { flare.severity = FlareSeverity.from(it).dbValue }
        update.notes?.let { flare.notes = it.takeIf(String::isNotBlank) }
        return flares.save(flare).toDto()
    }

    @Transactional
    fun deleteFlare(id: Long) {
        if (!flares.existsById(id)) throw NoSuchElementException("Flare $id not found")
        flares.deleteById(id)
    }

    private fun startFlare(
        day: LocalDate,
        severity: String?,
        notes: String?,
    ): FlareDto {
        flares.findFirstByEndDateIsNullOrderByStartDateDesc()?.let {
            return it.toDto() // already active: return it instead of opening a second one
        }
        requireNotNull(severity) { "Severity is required to start a flare (mild, moderate or severe)" }
        val flare =
            flares.save(
                FlareEntity(
                    startDate = day,
                    severity = FlareSeverity.from(severity).dbValue,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
        return flare.toDto()
    }

    private fun endFlare(day: LocalDate): FlareDto {
        val active =
            flares.findFirstByEndDateIsNullOrderByStartDateDesc()
                ?: throw IllegalArgumentException("There is no active flare to end")
        require(!day.isBefore(active.startDate)) { "End date cannot be before the start date" }
        active.endDate = day
        return flares.save(active).toDto()
    }

    private fun DiaryEntryEntity.toDto() =
        DiaryEntryDto(
            date = date.toString(),
            bristol = bristol,
            pain = pain,
            urgency = urgency,
            blood = blood,
            bowelMovements = bowelMovements,
            stress = stress,
            notes = notes,
        )

    private fun FlareEntity.toDto() = FlareDto(id!!, startDate.toString(), endDate?.toString(), severity, notes)

    companion object {
        private const val DEFAULT_QUERY_DAYS = 30
    }
}
