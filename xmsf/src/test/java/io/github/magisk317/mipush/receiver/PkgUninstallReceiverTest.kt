package io.github.magisk317.mipush.receiver

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.xiaomi.push.service.PushServiceConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class PkgUninstallReceiverTest {
    private lateinit var context: Application
    private val receiver = PkgUninstallReceiver()

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).clearStartedServices()
    }

    @Test
    fun `package data clear forwards the stock action and extra`() {
        receiver.onReceive(
            context,
            Intent(Intent.ACTION_PACKAGE_DATA_CLEARED, Uri.parse("package:com.example.target")),
        )

        val started = shadowOf(context).nextStartedService
        assertEquals(PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED, started.action)
        assertEquals(
            "com.example.target",
            started.getStringExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME),
        )
    }

    @Test
    fun `package add remains an xspace observation and does not enter push service`() {
        receiver.onReceive(
            context,
            Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:com.example.target")),
        )

        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun `replacement removal does not emit uninstall`() {
        receiver.onReceive(
            context,
            Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.example.target"))
                .putExtra(Intent.EXTRA_REPLACING, true),
        )

        assertNull(shadowOf(context).peekNextStartedService())
    }
}
