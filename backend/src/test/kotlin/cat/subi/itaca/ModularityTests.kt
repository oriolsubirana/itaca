package cat.subi.itaca

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Verificación de arquitectura: los bounded contexts (training, health,
 * finance, chat, ingestion) no pueden referenciarse entre sí directamente;
 * solo comunican vía eventos. `shared` es el único módulo abierto.
 */
class ModularityTests {

    private val modules = ApplicationModules.of(ItacaApplication::class.java)

    @Test
    fun `la estructura modular es valida`() {
        modules.verify()
    }

    @Test
    fun `genera la documentacion de modulos`() {
        Documenter(modules).writeDocumentation()
    }
}
