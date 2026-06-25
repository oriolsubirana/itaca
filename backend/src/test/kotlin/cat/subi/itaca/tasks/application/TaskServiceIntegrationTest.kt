package cat.subi.itaca.tasks.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class TaskServiceIntegrationTest {
    @Autowired
    lateinit var tasks: TaskService

    @Test
    fun `adds a task via chat and lists it as open, tagged chat`() {
        val result = tasks.addTask("Contestar a Emma sobre el catering", null, null)

        assertTrue(result.saved)
        val view = tasks.queryTasks(false)
        assertEquals(1, view.openCount)
        assertEquals("chat", view.open.single().source)
        assertFalse(view.open.single().done)
    }

    @Test
    fun `completes a task by a title fragment and moves it to done`() {
        tasks.addTask("Contestar a Emma sobre el catering", null, null)

        val result = tasks.completeTask(null, "emma")

        assertTrue(result.saved)
        assertEquals(0, tasks.list(includeDone = true).openCount)
        assertEquals(1, tasks.list(includeDone = true).done.size)
        assertTrue(
            tasks
                .list(includeDone = true)
                .done
                .single()
                .done,
        )
    }

    @Test
    fun `completing by an ambiguous fragment errors without changing anything`() {
        tasks.addTask("Contestar el correo de Emma", null, null)
        tasks.addTask("Reenviar el correo a Zaira", null, null)

        val result = tasks.completeTask(null, "correo")

        assertFalse(result.saved)
        assertTrue(result.error!!.contains("Several", ignoreCase = true))
        assertEquals(2, tasks.queryTasks(false).openCount)
    }

    @Test
    fun `query_tasks counts overdue open tasks`() {
        tasks.create(TaskCommand(title = "Pagar el seguro", dueDate = LocalDate.now().minusDays(2)))
        tasks.create(TaskCommand(title = "Sin fecha"))

        val view = tasks.queryTasks(false)

        assertEquals(2, view.openCount)
        assertEquals(1, view.overdueCount)
        assertTrue(view.open.first { it.title == "Pagar el seguro" }.overdue)
    }

    @Test
    fun `update edits the title and clears the due date`() {
        val created = tasks.create(TaskCommand(title = "Comprar pan", dueDate = LocalDate.now().plusDays(1)))

        val updated = tasks.update(created.id, TaskPatch(title = "Comprar pan integral", clearDueDate = true))

        assertEquals("Comprar pan integral", updated.title)
        assertEquals(null, updated.dueDate)
    }

    @Test
    fun `deletes a task`() {
        val created = tasks.create(TaskCommand(title = "Algo temporal"))

        tasks.delete(created.id)

        assertEquals(0, tasks.list(includeDone = true).openCount)
    }
}
