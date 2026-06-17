// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.nutrition.adapter.`in`.rest

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class NutritionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `logs a meal, normalizing the Spanish meal type`() {
        mockMvc
            .perform(
                post("/api/nutrition/meals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"mealType":"cena","description":"Salmón con verduras","onPlan":true}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.mealType").value("dinner"))
            .andExpect(jsonPath("$.onPlan").value(true))
    }

    @Test
    fun `rejects an unknown meal type with 400`() {
        mockMvc
            .perform(
                post("/api/nutrition/meals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"mealType":"brunch","description":"algo"}"""),
            ).andExpect(status().isBadRequest)
    }
}
