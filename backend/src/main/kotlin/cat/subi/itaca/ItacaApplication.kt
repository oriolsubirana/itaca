package cat.subi.itaca

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic(systemName = "Ítaca", sharedModules = ["shared"])
@SpringBootApplication
class ItacaApplication

fun main(args: Array<String>) {
    runApplication<ItacaApplication>(*args)
}
