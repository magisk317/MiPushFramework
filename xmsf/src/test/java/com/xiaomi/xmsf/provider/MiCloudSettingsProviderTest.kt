package com.xiaomi.xmsf.provider

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Process
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class MiCloudSettingsProviderTest {
    private val uri = Uri.parse("content://${MiCloudSettingsProvider.AUTHORITY}")
    private lateinit var provider: MiCloudSettingsProvider

    @BeforeEach
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MiCloudSettingsProvider.SETTINGS_FILE_NAME, 0).edit().clear().commit()
        context.getSharedPreferences(MiCloudSettingsProvider.LEGACY_SETTINGS_FILE_NAME, 0).edit().clear().commit()
        context.getSharedPreferences(MiCloudSettingsProvider.MIGRATION_STATE_FILE_NAME, 0).edit().clear().commit()
        provider = MiCloudSettingsProvider().apply {
            attachInfo(context, null)
        }
    }

    @Test
    fun `query returns single null row for missing key`() {
        val cursor = provider.query(
            uri,
            arrayOf(MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY),
            null,
            null,
            null,
        )

        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.columnCount)
            assertNull(it.getString(0))
            assertFalse(it.moveToNext())
        }
    }

    @Test
    fun `inserted allowed key can be queried`() {
        val values = ContentValues().apply {
            put(MiCloudSettingsProvider.MICLOUD_SETTINGS_KEY, MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY)
            put(MiCloudSettingsProvider.MICLOUD_SETTINGS_VALUE, "1")
        }

        assertEquals(uri, provider.insert(uri, values))

        val cursor = provider.query(
            uri,
            arrayOf(MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY),
            null,
            null,
            null,
        )
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("1", it.getString(0))
        }
    }

    @Test
    fun `query rejects multi key projection like stock provider`() {
        assertThrows(IllegalArgumentException::class.java) {
            provider.query(uri, arrayOf("a", "b"), null, null, null)
        }
    }

    @Test
    fun `legacy provider preferences migrate into stock sdk settings file`() {
        val context: Application = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MiCloudSettingsProvider.LEGACY_SETTINGS_FILE_NAME, 0)
            .edit()
            .putString(MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY, "legacy")
            .commit()
        val migratedProvider = MiCloudSettingsProvider().apply {
            attachInfo(context, null)
        }

        val cursor = migratedProvider.query(
            uri,
            arrayOf(MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY),
            null,
            null,
            null,
        )

        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("legacy", it.getString(0))
        }
        assertEquals(
            "legacy",
            context.getSharedPreferences(MiCloudSettingsProvider.SETTINGS_FILE_NAME, 0)
                .getString(MiCloudSettingsProvider.MICLOUD_NETWORK_AVAILABILITY_KEY, null),
        )
        assertTrue(
            context.getSharedPreferences(MiCloudSettingsProvider.MIGRATION_STATE_FILE_NAME, 0)
                .getBoolean(MiCloudSettingsProvider.MIGRATION_SUCCESS_KEY, false),
        )
    }

    @Test
    fun `service token caller must be privileged or hold cloud manager permission`() {
        assertTrue(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = Process.SYSTEM_UID,
                appUid = 20_001,
                hasCloudManagerPermission = false,
            ),
        )
        assertTrue(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = 20_001,
                appUid = 20_001,
                hasCloudManagerPermission = false,
            ),
        )
        assertTrue(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = 30_001,
                appUid = 20_001,
                hasCloudManagerPermission = true,
            ),
        )
        assertFalse(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = 30_001,
                appUid = 20_001,
                hasCloudManagerPermission = false,
            ),
        )
    }

    @Test
    fun `settings reads use the same trusted caller policy`() {
        assertTrue(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = Process.ROOT_UID,
                appUid = 20_001,
                hasCloudManagerPermission = false,
            ),
        )
        assertFalse(
            MiCloudSettingsProvider.isSensitiveCallerAllowed(
                callingUid = 30_001,
                appUid = 20_001,
                hasCloudManagerPermission = false,
            ),
        )
    }
}
