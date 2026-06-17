package cat.subi.itaca.nutrition.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Lenient parsing: the chat may pass English codes or Spanish words. */
class MealTypeTest {
    @Test
    fun `parses the English codes case-insensitively`() {
        assertEquals(MealType.LUNCH, MealType.from("LUNCH"))
        assertEquals(MealType.BREAKFAST, MealType.from("breakfast"))
    }

    @Test
    fun `parses common Spanish words`() {
        assertEquals(MealType.BREAKFAST, MealType.from("desayuno"))
        assertEquals(MealType.LUNCH, MealType.from("Comida"))
        assertEquals(MealType.DINNER, MealType.from("cena"))
        assertEquals(MealType.SNACK, MealType.from("merienda"))
    }

    @Test
    fun `wire form is the lowercase name`() {
        assertEquals("snack", MealType.SNACK.wire)
    }

    @Test
    fun `rejects an unknown meal type`() {
        assertFailsWith<IllegalArgumentException> { MealType.from("brunch") }
    }
}
