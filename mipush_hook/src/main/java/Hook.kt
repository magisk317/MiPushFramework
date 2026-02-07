@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
import android.content.Context
import androidx.startup.Initializer
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.smack.ConnectionConfiguration
import java.lang.reflect.Field

class Hook : Initializer<Unit> {
    override fun create(context: Context) {
        try {
            doHook()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    @Throws(Exception::class)
    private fun doHook() {
        fakeMIUISystem()
        avoidTracking()
        ensureDefaultXmppServerIsCnHost()
    }

    @Throws(Exception::class)
    private fun fakeMIUISystem() {
        hookField(MIUIUtils::class.java, "isMIUI", MIUIUtils.IS_MIUI)
    }

    @Throws(Exception::class)
    private fun avoidTracking() {
        hookField(DeviceInfo::class.java, "sCachedIMEI", "")
    }

    private fun ensureDefaultXmppServerIsCnHost() {
        ConnectionConfiguration.setXmppServerHost(ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P)
    }

    @Throws(Exception::class)
    private fun hookField(klass: Class<*>, field: String, value: Any) {
        val target: Field = klass.getDeclaredField(field)
        target.isAccessible = true
        target.set(null, value)
    }
}
