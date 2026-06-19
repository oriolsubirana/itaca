// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.nutrition.adapter.`in`.rest

import cat.subi.itaca.TestcontainersConfiguration
import cat.subi.itaca.nutrition.application.MealAnalysis
import cat.subi.itaca.nutrition.application.MealAnalyzer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, NutritionControllerTest.StubAnalyzer::class)
class NutritionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `logs a meal, normalizing the Spanish meal type`() {
        mockMvc
            .perform(
                post("/api/nutrition/meals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"mealType":"cena","description":"Salmón","onPlan":true,"calories":620}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.mealType").value("dinner"))
            .andExpect(jsonPath("$.onPlan").value(true))
            .andExpect(jsonPath("$.calories").value(620))
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

    @Test
    fun `analyzes a meal photo into a reviewable proposal`() {
        val photo = MockMultipartFile("file", "comida.jpg", "image/jpeg", "fake-image-bytes".toByteArray())

        mockMvc
            .perform(multipart("/api/nutrition/meals/photo").file(photo))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("Salmón a la plancha con brócoli"))
            .andExpect(jsonPath("$.calories").value(600))
            .andExpect(jsonPath("$.macros").value("P 42 · C 20 · G 25"))
            .andExpect(jsonPath("$.mealType").value("dinner"))
            .andExpect(jsonPath("$.onPlan").value(true))
    }

    @Test
    fun `estimates calories and macros from a text description`() {
        mockMvc
            .perform(
                post("/api/nutrition/meals/estimate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"description":"Arroz con pollo y verduras"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.calories").value(540))
            .andExpect(jsonPath("$.macros").value("P 30 · C 60 · G 15"))
    }

    /** Replaces the real Anthropic analyzer so the endpoint tests make no API call. */
    @TestConfiguration
    class StubAnalyzer {
        private val photo = MealAnalysis("Salmón a la plancha con brócoli", 600, "P 42 · C 20 · G 25", "dinner", true)
        private val text = MealAnalysis("Arroz con pollo", 540, "P 30 · C 60 · G 15", "lunch", true)

        @Bean
        @Primary
        fun stubMealAnalyzer(): MealAnalyzer =
            object : MealAnalyzer {
                override fun fromPhoto(
                    image: ByteArray,
                    mimeType: String,
                ): MealAnalysis = photo

                override fun fromText(description: String): MealAnalysis = text
            }
    }
}
