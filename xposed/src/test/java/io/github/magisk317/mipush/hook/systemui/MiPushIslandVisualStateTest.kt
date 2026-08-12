package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.common.island.IslandRendererMode
import io.github.magisk317.mipush.common.island.IslandVisualContract
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MiPushIslandVisualStateTest {
    @BeforeEach
    fun setUp() {
        IslandPreferences.resetForTest()
        MiPushIslandVisualState.clear()
    }

    @AfterEach
    fun tearDown() {
        MiPushIslandVisualState.clear()
        IslandPreferences.resetForTest()
    }

    @Test
    fun `only the versioned MiPush renderer contract is accepted`() {
        MiPushIslandVisualState.recordForTest(
            key = "one",
            sourcePackage = "app.one",
            extras = extras(),
            options = IslandOptions(),
        )
        assertNotNull(MiPushIslandVisualState.snapshotForKey("one"))
        MiPushIslandVisualState.recordForTest(
            key = "hyper",
            sourcePackage = "app.hyper",
            extras = extras(owner = IslandVisualContract.HYPERISLAND_OWNER),
            options = IslandOptions(),
        )
        assertNull(MiPushIslandVisualState.snapshotForKey("hyper"))

        MiPushIslandVisualState.recordForTest(
            key = "old",
            sourcePackage = "app.old",
            extras = extras(version = IslandVisualContract.VERSION - 1),
            options = IslandOptions(),
        )
        assertNull(MiPushIslandVisualState.snapshotForKey("old"))
    }

    @Test
    fun `ambiguous active notifications do not leak one apps color into another island`() {
        MiPushIslandVisualState.recordForTest(
            key = "one",
            sourcePackage = "app.one",
            extras = extras(color = "#FFFF0000"),
            options = IslandOptions(),
        )
        MiPushIslandVisualState.recordForTest(
            key = "two",
            sourcePackage = "app.two",
            extras = extras(color = "#FF00FF00"),
            options = IslandOptions(),
        )

        assertNull(MiPushIslandVisualState.current())
        assertEquals("#FFFF0000", MiPushIslandVisualState.snapshotForKey("one")?.accentColor)
        assertEquals("#FF00FF00", MiPushIslandVisualState.snapshotForKey("two")?.accentColor)
    }

    @Test
    fun `disabling visuals removes an existing key before the next draw`() {
        MiPushIslandVisualState.recordForTest(
            key = "one",
            sourcePackage = "app.one",
            extras = extras(),
            options = IslandOptions(),
        )
        assertNotNull(MiPushIslandVisualState.snapshotForKey("one"))

        MiPushIslandVisualState.recordForTest(
            key = "one",
            sourcePackage = "app.one",
            extras = extras(),
            options = IslandOptions(visualEnabled = false),
        )

        assertNull(MiPushIslandVisualState.snapshotForKey("one"))
        assertNull(MiPushIslandVisualState.current())
    }

    @Test
    fun `visual snapshot keeps its captured user configuration`() {
        MiPushIslandVisualState.recordForTest(
            key = "work-profile",
            sourcePackage = "app.work",
            extras = extras(color = "#FF00FF00"),
            options = IslandOptions(visualEnabled = true),
        )

        assertTrue(MiPushIslandVisualState.current()?.options?.visualEnabled == true)
        assertEquals("#FF00FF00", MiPushIslandVisualState.snapshotForKey("work-profile")?.accentColor)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `fallback visual keys keep same notification identity isolated by user`() {
        val primary = statusBarNotification(userId = 0)
        val cloned = statusBarNotification(userId = 999)

        assertEquals("0|app.same:42:tag", MiPushIslandVisualState.fallbackVisualKey(0, "app.same", 42, "tag"))
        assertEquals("999|app.same:42:tag", MiPushIslandVisualState.fallbackVisualKey(999, "app.same", 42, "tag"))

        MiPushIslandVisualState.recordForTest(
            key = MiPushIslandVisualState.visualKey(primary),
            sourcePackage = "app.same",
            extras = primary.notification.extras,
            options = IslandOptions(),
        )
        MiPushIslandVisualState.recordForTest(
            key = MiPushIslandVisualState.visualKey(cloned),
            sourcePackage = "app.same",
            extras = cloned.notification.extras,
            options = IslandOptions(),
        )

        assertEquals(2, listOf(primary, cloned).map(MiPushIslandVisualState::visualKey).distinct().size)
        assertNotNull(MiPushIslandVisualState.snapshotForKey(MiPushIslandVisualState.visualKey(primary)))
        assertNotNull(MiPushIslandVisualState.snapshotForKey(MiPushIslandVisualState.visualKey(cloned)))

        MiPushIslandVisualState.remove(primary)

        assertNull(MiPushIslandVisualState.snapshotForKey(MiPushIslandVisualState.visualKey(primary)))
        assertNotNull(MiPushIslandVisualState.snapshotForKey(MiPushIslandVisualState.visualKey(cloned)))
    }

    @Suppress("DEPRECATION")
    private fun statusBarNotification(userId: Int): StatusBarNotification {
        val notification = Notification().apply {
            extras.putInt(IslandVisualContract.VISUAL_VERSION_KEY, IslandVisualContract.VERSION)
            extras.putString(IslandVisualContract.VISUAL_MARKER_KEY, IslandVisualContract.VISUAL_MARKER)
            extras.putString(IslandVisualContract.OWNER_KEY, IslandVisualContract.MIPUSH_OWNER)
            extras.putString(IslandVisualContract.VISUAL_MODE_KEY, IslandRendererMode.MIPUSH.wireValue)
            extras.putString(IslandVisualContract.HIGHLIGHT_COLOR_KEY, "#FF00C8FF")
            extras.putString(IslandDispatchContract.SOURCE_PACKAGE, "app.same")
        }
        return StatusBarNotification(
            "app.same",
            "app.same",
            42,
            "tag",
            10_000,
            123,
            0,
            notification,
            userHandle(userId),
            1_000L,
        )
    }

    private fun userHandle(identifier: Int): UserHandle =
        UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.newInstance(identifier)

    private fun extras(
        owner: String = IslandVisualContract.MIPUSH_OWNER,
        mode: String = IslandRendererMode.MIPUSH.wireValue,
        version: Int = IslandVisualContract.VERSION,
        color: String = "#FF00C8FF",
    ) = Bundle().apply {
        putInt(IslandVisualContract.VISUAL_VERSION_KEY, version)
        putString(IslandVisualContract.VISUAL_MARKER_KEY, IslandVisualContract.VISUAL_MARKER)
        putString(IslandVisualContract.OWNER_KEY, owner)
        putString(IslandVisualContract.VISUAL_MODE_KEY, mode)
        putString(IslandVisualContract.HIGHLIGHT_COLOR_KEY, color)
        putString(IslandDispatchContract.SOURCE_PACKAGE, "app.one")
    }

}
