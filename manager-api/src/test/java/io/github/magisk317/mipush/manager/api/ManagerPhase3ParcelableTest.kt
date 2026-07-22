package io.github.magisk317.mipush.manager.api

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerPhase3ParcelableTest {
    @Test
    fun `preference and upload parcelables round trip`() {
        val entry = ManagerPreferenceEntryDto(
            key = "theme_mode",
            type = "int",
            value = "1",
            owner = "manager",
        )
        assertEquals(entry, roundTrip(entry, ManagerPreferenceEntryDto.CREATOR))
        assertEquals(
            ManagerRuntimePreferencesDto(entries = listOf(entry.copy(owner = "runtime", key = "xmpp_server"))),
            roundTrip(
                ManagerRuntimePreferencesDto(entries = listOf(entry.copy(owner = "runtime", key = "xmpp_server"))),
                ManagerRuntimePreferencesDto.CREATOR,
            ),
        )
        assertEquals(
            ManagerMigrationSnapshotDto(entries = listOf(entry)),
            roundTrip(ManagerMigrationSnapshotDto(entries = listOf(entry)), ManagerMigrationSnapshotDto.CREATOR),
        )
        assertEquals(
            ManagerConfigurationUploadResultDto(success = true, activated = true, details = "ok"),
            roundTrip(
                ManagerConfigurationUploadResultDto(success = true, activated = true, details = "ok"),
                ManagerConfigurationUploadResultDto.CREATOR,
            ),
        )
    }

    @Test
    fun `protocol recognizes phase 3 capabilities`() {
        assertEquals(3, ManagerProtocol.MINOR)
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES))
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_CONFIGURATION_UPLOAD))
        assertEquals(
            "configuration_upload_missing_descriptor",
            ManagerProtocol.validateConfigurationUploadRequest(
                ManagerConfigurationUploadRequestDto(path = "a.json", contentLength = 1),
            ),
        )
    }

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
