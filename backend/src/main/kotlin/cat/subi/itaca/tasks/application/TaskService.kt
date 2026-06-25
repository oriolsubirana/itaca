package cat.subi.itaca.tasks.application

import cat.subi.itaca.shared.chat.ChatTools
import cat.subi.itaca.tasks.adapter.out.persistence.TaskEntity
import cat.subi.itaca.tasks.adapter.out.persistence.TaskRepository
import cat.subi.itaca.tasks.domain.TaskSource
import cat.subi.itaca.tasks.domain.TaskTitle
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

data class TaskDto(
    val id: Long,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val done: Boolean,
    val doneAt: String?,
    val source: String,
    val createdAt: String,
    /** Computed: not done and the due date is already past. */
    val overdue: Boolean,
)

/** Open and (optionally) completed tasks, with the open/overdue counts for the Home glance. */
data class TasksView(
    val open: List<TaskDto>,
    val done: List<TaskDto>,
    val openCount: Int,
    val overdueCount: Int,
)

/** A task to create (shared by the chat tool and the REST form). */
data class TaskCommand(
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val source: TaskSource = TaskSource.MANUAL,
)

/** Partial edit: null leaves a field as is; `clearDueDate` drops the date; `done` toggles completion. */
data class TaskPatch(
    val title: String? = null,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val clearDueDate: Boolean = false,
    val done: Boolean? = null,
)

/** Chat-tool outcome: the model gets a structured result instead of throwing. */
data class TaskResult(
    val saved: Boolean,
    val task: TaskDto? = null,
    val error: String? = null,
)

/**
 * Application service of the tasks context: a personal to-do list, exposed to the chat as tools and
 * reused by the REST adapter. Plain CRUD over a single-user table; the only domain rules are a
 * non-blank title and the computed "overdue" flag.
 */
@Service
class TaskService(
    private val tasks: TaskRepository,
) : ChatTools {
    @Tool(
        name = "add_task",
        description =
            "Adds a pending task to Oriol's to-do list. title is what needs doing (free text). " +
                "dueDate is an optional deadline (YYYY-MM-DD). Use it whenever he asks to be reminded of " +
                "something or mentions a pending to-do.",
    )
    @Transactional
    fun addTask(
        @ToolParam(description = "What needs doing (free text)") title: String,
        @ToolParam(description = "Optional deadline, YYYY-MM-DD", required = false) dueDate: String?,
        @ToolParam(description = "Optional free-text notes", required = false) notes: String?,
    ): TaskResult =
        runCatching {
            val task =
                create(
                    TaskCommand(
                        title = title,
                        notes = notes,
                        dueDate = dueDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        source = TaskSource.CHAT,
                    ),
                )
            TaskResult(saved = true, task = task)
        }.getOrElse { TaskResult(saved = false, error = it.message) }

    @Tool(
        name = "complete_task",
        description =
            "Marks a pending task as done. Pass its id when known; otherwise pass a title fragment and it " +
                "completes the single open task that matches (it errors if none or several match).",
    )
    @Transactional
    fun completeTask(
        @ToolParam(description = "Task id, if known", required = false) id: Long?,
        @ToolParam(description = "Part of the task title, if the id is unknown", required = false) title: String?,
    ): TaskResult =
        runCatching {
            val entity = resolveOpenTask(id, title)
            TaskResult(saved = true, task = markDone(entity).toDto())
        }.getOrElse { TaskResult(saved = false, error = it.message) }

    @Tool(
        name = "query_tasks",
        description =
            "Returns Oriol's pending tasks (and completed ones if includeDone is true), with how many are " +
                "open and how many are overdue. Use it to review or follow up on to-dos.",
    )
    fun queryTasks(
        @ToolParam(description = "Include completed tasks too (default false)", required = false) includeDone: Boolean?,
    ): TasksView = list(includeDone ?: false)

    @Transactional
    fun create(command: TaskCommand): TaskDto {
        val title = TaskTitle.of(command.title)
        val saved =
            tasks.save(
                TaskEntity(
                    title = title.value,
                    notes = command.notes?.trim()?.takeIf { it.isNotBlank() },
                    dueDate = command.dueDate,
                    source = command.source.wire,
                ),
            )
        return saved.toDto()
    }

    @Transactional
    fun update(
        id: Long,
        patch: TaskPatch,
    ): TaskDto {
        val task = tasks.findById(id).orElseThrow { NoSuchElementException("Task $id not found") }
        patch.title?.let { task.title = TaskTitle.of(it).value }
        patch.notes?.let { task.notes = it.trim().takeIf(String::isNotBlank) }
        when {
            patch.clearDueDate -> task.dueDate = null
            patch.dueDate != null -> task.dueDate = patch.dueDate
        }
        patch.done?.let { if (it) markDone(task) else reopen(task) }
        return tasks.save(task).toDto()
    }

    @Transactional
    fun delete(id: Long) {
        if (!tasks.existsById(id)) throw NoSuchElementException("Task $id not found")
        tasks.deleteById(id)
    }

    fun list(includeDone: Boolean): TasksView {
        val open = tasks.findByDoneOrderByCreatedAtDesc(false).map { it.toDto() }.sortedWith(OPEN_ORDER)
        val done = if (includeDone) tasks.findByDoneOrderByCreatedAtDesc(true).map { it.toDto() } else emptyList()
        return TasksView(open, done, open.size, open.count { it.overdue })
    }

    private fun resolveOpenTask(
        id: Long?,
        title: String?,
    ): TaskEntity {
        if (id != null) {
            val task = tasks.findById(id).orElseThrow { NoSuchElementException("Task $id not found") }
            require(!task.done) { "Task $id is already done" }
            return task
        }
        val query = title?.trim().orEmpty()
        require(query.isNotBlank()) { "Provide a task id or a title fragment" }
        val matches =
            tasks.findByDoneOrderByCreatedAtDesc(false).filter { it.title.contains(query, ignoreCase = true) }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw NoSuchElementException("No open task matches '$query'")
            else -> throw IllegalArgumentException("Several open tasks match '$query'; be more specific")
        }
    }

    private fun markDone(task: TaskEntity): TaskEntity {
        task.done = true
        task.doneAt = Instant.now()
        return task
    }

    private fun reopen(task: TaskEntity) {
        task.done = false
        task.doneAt = null
    }

    private fun TaskEntity.toDto(): TaskDto {
        val due = dueDate
        return TaskDto(
            id = id!!,
            title = title,
            notes = notes,
            dueDate = due?.toString(),
            done = done,
            doneAt = doneAt?.toString(),
            source = source,
            createdAt = createdAt.toString(),
            overdue = !done && due != null && due.isBefore(LocalDate.now()),
        )
    }

    private companion object {
        // Open tasks: soonest deadline first, undated last, then most recently created.
        val OPEN_ORDER =
            compareBy<TaskDto> { it.dueDate ?: "9999-12-31" }.thenByDescending { it.createdAt }
    }
}
