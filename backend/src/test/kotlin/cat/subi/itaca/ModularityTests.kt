package cat.subi.itaca

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Architecture verification: bounded contexts (training, health, finance,
 * chat, ingestion) must not reference each other directly; they only
 * communicate via events. `shared` is the only open module.
 */
class ModularityTests {
    private val modules = ApplicationModules.of(ItacaApplication::class.java)

    @Test
    fun `modular structure is valid`() {
        modules.verify()
    }

    @Test
    fun `generates module documentation`() {
        Documenter(modules).writeDocumentation()
    }
}
