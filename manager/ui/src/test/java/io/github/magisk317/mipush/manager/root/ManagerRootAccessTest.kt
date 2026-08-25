package io.github.magisk317.mipush.manager.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerRootAccessTest {
    @Test
    fun `force stop command is scoped to requested user`() {
        assertEquals(
            "am force-stop --user 999 com.xiaomi.xmsf",
            ManagerRootAccess.forceStopXmsfCommand(999),
        )
    }

    @Test
    fun `recovery commands are scoped to requested user`() {
        assertEquals(
            "pm unstop --user 999 com.xiaomi.xmsf",
            ManagerRootAccess.unstopXmsfCommand(999),
        )
        assertEquals(
            "am broadcast --user 999 -a android.intent.action.BOOT_COMPLETED " +
                "-n com.xiaomi.xmsf/io.github.magisk317.mipush.receiver.BootReceiver",
            ManagerRootAccess.bootCompletedXmsfCommand(999),
        )
    }
}
