package io.github.magisk317.mipush.telemetry

import android.app.Application
import com.xiaomi.push.providers.TrafficDatabaseHelper
import com.xiaomi.smack.util.TrafficUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35], application = Application::class)
class TelemetryDisablerTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @AfterEach
    fun restoreVendorDefault() {
        TrafficUtils.configureTrafficCollection(context, enabled = true)
        context.deleteDatabase(TrafficDatabaseHelper.DATABASE_NAME)
    }

    @Test
    fun `disable all closes traffic path and removes data from older builds`() {
        TrafficUtils.configureTrafficCollection(context, enabled = true)
        TrafficDatabaseHelper(context).apply {
            writableDatabase.execSQL(
                "INSERT INTO traffic(package_name, message_ts, bytes, network_type, rcv, imsi) " +
                    "VALUES ('target.package', 1, 2, 0, 1, 'sensitive')",
            )
            close()
        }
        assertTrue(context.getDatabasePath(TrafficDatabaseHelper.DATABASE_NAME).exists())

        TelemetryDisabler.disableAll(context)

        assertFalse(TrafficUtils.isTrafficCollectionEnabled())
        assertFalse(context.getDatabasePath(TrafficDatabaseHelper.DATABASE_NAME).exists())
    }

    @Test
    fun `disabled traffic insert cannot recreate database`() {
        TrafficUtils.configureTrafficCollection(context, enabled = false)

        TrafficUtils.insertTraffic(
            context,
            listOf(
                TrafficUtils.TrafficInfo(
                    packageName = "target.package",
                    messageTs = 1,
                    networkType = 0,
                    rcv = 1,
                    imsi = "sensitive",
                    bytes = 2,
                ),
            ),
        )

        assertFalse(context.getDatabasePath(TrafficDatabaseHelper.DATABASE_NAME).exists())
    }
}
