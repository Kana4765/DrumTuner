package com.drumtuner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniJsonTest {

    @Test
    fun roundTripsNestedValues() {
        val value = linkedMapOf(
            "s" to "tab\t\"quote\" \\ é ✓",
            "n" to 182.4,
            "i" to 10,
            "neg" to -3.5e-3,
            "b" to true,
            "z" to null,
            "a" to listOf(1, "two", listOf<Any?>(), mapOf<String, Any?>()),
        )
        val text = MiniJson.stringify(value)
        val back = MiniJson.parse(text) as Map<*, *>
        assertEquals("tab\t\"quote\" \\ é ✓", back["s"])
        assertEquals(182.4, back["n"] as Double, 0.0)
        assertEquals(10.0, back["i"] as Double, 0.0)
        assertEquals(-3.5e-3, back["neg"] as Double, 0.0)
        assertEquals(true, back["b"])
        assertEquals(null, back["z"])
        assertEquals(listOf(1.0, "two", emptyList<Any?>(), emptyMap<String, Any?>()), back["a"])
    }

    @Test
    fun parsesUnicodeEscapesAndCompactInput() {
        assertEquals(mapOf("k" to "A✓"), MiniJson.parse("{\"k\":\"\\u0041\\u2713\"}"))
        assertEquals(listOf(1.0, 2.0), MiniJson.parse(" [1 , 2] "))
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(MiniJson.ParseException::class.java) { MiniJson.parse("{\"a\":}") }
        assertThrows(MiniJson.ParseException::class.java) { MiniJson.parse("[1,2") }
        assertThrows(MiniJson.ParseException::class.java) { MiniJson.parse("{} x") }
    }

    @Test
    fun kitDecodeToleratesMissingFields() {
        val kits = KitJson.decode("""{"kits":[{"id":"a","shells":[{"drum":"KICK","diameterIn":24}]}]}""")
        assertEquals(1, kits.size)
        assertEquals(24.0, kits[0].shell(com.drumtuner.app.tuning.DrumType.KICK).diameterIn, 0.0)
        assertEquals(5, kits[0].shells.size) // normalised to all five drum slots
    }
}
