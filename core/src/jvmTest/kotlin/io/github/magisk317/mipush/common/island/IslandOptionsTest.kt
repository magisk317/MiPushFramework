package io.github.magisk317.mipush.common.island

import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.MONOCHROME_SHADE_ICON_KEY
import io.github.magisk317.mipush.common.NOTIFICATION_COUNT_LIMIT_EXEMPT_KEY
import io.github.magisk317.mipush.common.island.NATIVE_LIVE_UPDATE_ENABLED_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IslandOptionsTest {
    @Test
    fun `preference flags preserve retained wire values and normalize timeout`() {
        val flags = IslandOptions(
            enabled = false,
            timeoutSecs = 0,
            focusNotification = true,
            monochromeShadeIcon = true,
        ).toPreferenceFlags()

        assertEquals("0", flags.getValue(ISLAND_PREF_ENABLED))
        assertEquals("1", flags.getValue(ISLAND_PREF_TIMEOUT))
        assertEquals("1", flags.getValue(ISLAND_PREF_FOCUS_NOTIF))
        assertEquals("1", flags.getValue(MONOCHROME_SHADE_ICON_KEY))
        assertEquals("0", flags.getValue(NOTIFICATION_COUNT_LIMIT_EXEMPT_KEY))
    }

    @Test
    fun `count limit exemption flag round-trips through preference flags`() {
        val flags = IslandOptions(notificationCountLimitExempt = true).toPreferenceFlags()

        assertEquals("1", flags.getValue(NOTIFICATION_COUNT_LIMIT_EXEMPT_KEY))
    }

    @Test
    fun `native live update preference flag defaults off and can be enabled`() {
        assertEquals("0", IslandOptions().toPreferenceFlags().getValue(NATIVE_LIVE_UPDATE_ENABLED_KEY))
        assertEquals(
            "1",
            IslandOptions(nativeLiveUpdateEnabled = true).toPreferenceFlags()
                .getValue(NATIVE_LIVE_UPDATE_ENABLED_KEY),
        )
    }
}
