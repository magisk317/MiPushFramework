package io.github.magisk317.mipush.common.configurations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigJsonTest {
    @Test
    fun `generic get is strict while opt is tolerant`() {
        val objectValue = ConfigJsonObject(
            """{"nullValue":null,"number":42,"quoted":"42","nested":{"value":1}}""",
        )

        assertEquals(42L, objectValue.get("number"))
        assertEquals("42", objectValue.get("quoted"))
        assertNull(objectValue.get("nullValue"))
        assertTrue(objectValue.get("nested") is ConfigJsonObject)
        assertThrows<ConfigJsonException> { objectValue.get("missing") }

        assertNull(objectValue.opt("missing"))
        assertNull(objectValue.opt("nullValue"))
        assertEquals("42", objectValue.opt("quoted"))
        assertFalse(objectValue.has("missing"))
        assertTrue(objectValue.has("nullValue"))
        assertTrue(objectValue.isNull("nullValue"))
        assertTrue(objectValue.isNull("missing"))
    }

    @Test
    fun `array get distinguishes null from out of bounds`() {
        val array = ConfigJson.parse("[null,\"value\",3]") as ConfigJsonArray

        assertNull(array.get(0))
        assertEquals("value", array.get(1))
        assertEquals(3L, array.get(2))
        assertThrows<ConfigJsonException> { array.get(-1) }
        assertThrows<ConfigJsonException> { array.get(3) }
        assertNull(array.opt(-1))
        assertNull(array.opt(3))
        assertEquals(0, array.optInt(0))
    }

    @Test
    fun `json primitive matrix preserves strings and numeric types`() {
        val objectValue = ConfigJsonObject(
            """{"bool":true,"integer":42,"long":9223372036854775807,"fraction":1.5,"exponent":1e3,"quoted":"42"}""",
        )

        assertEquals(true, objectValue.get("bool"))
        assertEquals(42L, objectValue.get("integer"))
        assertEquals(Long.MAX_VALUE, objectValue.get("long"))
        assertEquals(1.5, objectValue.get("fraction"))
        assertEquals(1000.0, objectValue.get("exponent"))
        assertEquals("42", objectValue.get("quoted"))

        assertEquals(42, objectValue.getInt("quoted"))
        assertEquals(42, objectValue.optInt("quoted"))
        assertEquals("42", objectValue.optString("integer"))
    }

    @Test
    fun `overflow and non finite numbers are rejected without precision loss`() {
        assertThrows<ConfigJsonException> {
            ConfigJson.parse("9223372036854775808")
        }
        assertThrows<ConfigJsonException> {
            ConfigJson.parse("1e400")
        }

        val objectValue = ConfigJsonObject(
            """{"overflow":9223372036854775808,"infinity":1e400,"safe":9007199254740993}""",
        )
        assertThrows<ConfigJsonException> { objectValue.get("overflow") }
        assertThrows<ConfigJsonException> { objectValue.getDouble("infinity") }
        assertNull(objectValue.opt("overflow"))
        assertEquals(0, objectValue.optInt("overflow"))
        assertEquals(0.0, objectValue.optDouble("infinity"))
        assertEquals(9007199254740993L, objectValue.get("safe"))

        assertThrows<ConfigJsonException> {
            ConfigJsonObject().put("nan", Double.NaN)
        }
        assertThrows<ConfigJsonException> {
            ConfigJsonObject().put("infinity", Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `opt typed accessors return defaults for malformed values`() {
        val objectValue = ConfigJsonObject(
            """{"badInt":"not-an-int","badBoolean":{},"badLong":1.5,"badDouble":[]}""",
        )

        assertEquals(0, objectValue.optInt("badInt"))
        assertFalse(objectValue.optBoolean("badBoolean"))
        assertEquals(0L, objectValue.optLong("badLong"))
        assertEquals(0.0, objectValue.optDouble("badDouble"))
        assertEquals("{}", objectValue.optString("badBoolean"))
        assertEquals("", objectValue.optString("missing"))
    }

    @Test
    fun `put preserves quoted numeric strings and exact long values`() {
        val objectValue = ConfigJsonObject()
            .put("quoted", "9007199254740993")
            .put("exact", 9007199254740993L)

        assertEquals("9007199254740993", objectValue.get("quoted"))
        assertEquals(9007199254740993L, objectValue.get("exact"))
    }
}
