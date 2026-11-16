package io.github.magisk317.mipush.manager.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ManagerApplicationSerializationTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `manager application round trips through the shared port`() {
        val application = ManagerApplication(
            id = 42L,
            userId = 999,
            packageName = "com.example.client",
            type = ManagerApplication.Type.ALLOW,
            notificationOnRegister = true,
            blocked = true,
            islandEnabled = false,
            islandFocusNotification = true,
            registeredType = ManagerApplication.RegisteredType.REGISTERED,
            existServices = true,
            appName = "Example",
            appNamePinYin = "Example",
            lastReceiveTimeMs = 1234L,
            registrationPending = true,
        )

        val encoded = json.encodeToJsonElement(application)
        val decoded = json.decodeFromJsonElement<ManagerApplication>(encoded)

        assertEquals(application, decoded)
        assertTrue(encoded.jsonObject.containsKey("userId"))
        assertTrue(encoded.jsonObject.getValue("registrationPending").jsonPrimitive.boolean)
    }

    @Test
    fun `explicit legacy ask and deny values survive decoding without being replaced by the default`() {
        for (type in listOf(ManagerApplication.Type.ASK, ManagerApplication.Type.DENY)) {
            val encoded = json.parseToJsonElement("""{"packageName":"com.example.client","type":$type}""")
            val decoded = json.decodeFromJsonElement<ManagerApplication>(encoded)
            assertEquals(type, decoded.type)
            assertFalse(decoded.registrationPending)
        }
    }

    @Test
    fun `missing policy keeps automatic behavior and missing pending flag stays false`() {
        val encoded = json.parseToJsonElement(
            """
            {
              "packageName": "com.example.client",
              "futureField": "ignored"
            }
            """.trimIndent(),
        )

        val decoded = json.decodeFromJsonElement<ManagerApplication>(encoded)

        assertEquals("com.example.client", decoded.packageName)
        assertEquals(ManagerApplication.Type.ALLOW, decoded.type)
        assertFalse(decoded.registrationPending)
    }
}
