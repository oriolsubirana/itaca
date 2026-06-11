package cat.subi.itaca.health.domain

/**
 * Valor de la escala de Bristol (1-7) de una entrada del diario.
 */
@JvmInline
value class BristolScale private constructor(val value: Int) {

    companion object {
        fun of(value: Int): BristolScale {
            require(value in 1..7) { "La escala de Bristol va de 1 a 7: $value" }
            return BristolScale(value)
        }
    }
}
