// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.tasks.adapter.`in`.rest

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TaskControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var json: ObjectMapper

    private fun createId(body: String): Long {
        val response =
            mockMvc
                .perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return json.readTree(response).get("id").asLong()
    }

    @Test
    fun `creates a task and returns it not done`() {
        mockMvc
            .perform(
                post("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Llamar al gestor","dueDate":"2026-07-01"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Llamar al gestor"))
            .andExpect(jsonPath("$.dueDate").value("2026-07-01"))
            .andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.source").value("manual"))
    }

    @Test
    fun `rejects a blank title with 400`() {
        mockMvc
            .perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""{"title":"  "}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `completes a task via patch`() {
        val id = createId("""{"title":"Revisar la analítica"}""")

        mockMvc
            .perform(
                patch("/api/tasks/$id").contentType(MediaType.APPLICATION_JSON).content("""{"done":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneAt").isNotEmpty)
    }

    @Test
    fun `deletes a task`() {
        val id = createId("""{"title":"Tarea efímera"}""")

        mockMvc.perform(delete("/api/tasks/$id")).andExpect(status().isNoContent)
        mockMvc
            .perform(get("/api/tasks").param("includeDone", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.open[?(@.id == $id)]").doesNotExist())
    }

    @Test
    fun `patching a missing task returns 404`() {
        mockMvc
            .perform(patch("/api/tasks/999999").contentType(MediaType.APPLICATION_JSON).content("""{"done":true}"""))
            .andExpect(status().isNotFound)
    }
}
