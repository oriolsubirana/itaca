// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.tasks.adapter.`in`.rest

import cat.subi.itaca.tasks.application.TaskCommand
import cat.subi.itaca.tasks.application.TaskDto
import cat.subi.itaca.tasks.application.TaskPatch
import cat.subi.itaca.tasks.application.TaskService
import cat.subi.itaca.tasks.application.TasksView
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class CreateTaskRequest(
    val title: String = "",
    val notes: String? = null,
    val dueDate: String? = null,
) {
    fun toCommand(): TaskCommand =
        TaskCommand(
            title = title,
            notes = notes,
            dueDate = dueDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        )
}

/** PATCH body: an omitted field stays as is; a blank `dueDate` clears the deadline. */
data class UpdateTaskRequest(
    val title: String? = null,
    val notes: String? = null,
    val dueDate: String? = null,
    val done: Boolean? = null,
) {
    fun toPatch(): TaskPatch =
        TaskPatch(
            title = title,
            notes = notes,
            dueDate = dueDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            clearDueDate = dueDate != null && dueDate.isBlank(),
            done = done,
        )
}

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val tasks: TaskService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) includeDone: Boolean?,
    ): TasksView = tasks.list(includeDone ?: true)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateTaskRequest,
    ): TaskDto = tasks.create(request.toCommand())

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: UpdateTaskRequest,
    ): TaskDto = tasks.update(id, request.toPatch())

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) = tasks.delete(id)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NoSuchElementException): Map<String, String?> = mapOf("error" to e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: IllegalArgumentException): Map<String, String?> = mapOf("error" to e.message)
}
