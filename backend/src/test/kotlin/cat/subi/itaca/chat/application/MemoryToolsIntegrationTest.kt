package cat.subi.itaca.chat.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class MemoryToolsIntegrationTest {
    @Autowired
    lateinit var tools: MemoryTools

    @Test
    fun `saves, lists and forgets durable facts`() {
        val saved = tools.saveMemory("Toma Mezavant 2 x 1,2 g por la noche (2,4 g/día)")
        assertTrue(saved.saved)

        val all = tools.allMemories()
        assertTrue(all.any { it.content.contains("Mezavant") })

        val forgotten = tools.forgetMemory(saved.memory!!.id)
        assertTrue(forgotten.saved)
        assertTrue(tools.allMemories().none { it.id == saved.memory!!.id })
    }

    @Test
    fun `rejects empty memories and unknown ids`() {
        assertFalse(tools.saveMemory("   ").saved)
        assertFalse(tools.forgetMemory(99_999).saved)
    }

    @Test
    fun `saved memories are injected into the system prompt with their ids`() {
        val saved = tools.saveMemory("Vive en Zúrich; moneda base CHF")

        val prompt = SystemPrompts.forMode("general", tools.allMemories())

        assertTrue(prompt.contains("[${saved.memory!!.id}] Vive en Zúrich; moneda base CHF"))
        assertTrue(prompt.contains("Fecha de hoy:"))
    }

    @Test
    fun `empty memory renders an explicit empty section`() {
        val prompt = SystemPrompts.forMode("general", emptyList())
        assertEquals(true, prompt.contains("Memoria del usuario: (vacía todavía)"))
    }
}
