@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
import android.content.Context
import androidx.startup.Initializer
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
        hookFieldIfPresent("com.xiaomi.channel.commonutils.android.MIUIUtils", "isMIUI", true)
    }

    @Throws(Exception::class)
    private fun avoidTracking() {
        hookFieldIfPresent("com.xiaomi.channel.commonutils.android.DeviceInfo", "sCachedIMEI", "")
    }

    private fun ensureDefaultXmppServerIsCnHost() {
        runCatching {
            val klass = Class.forName("com.xiaomi.smack.ConnectionConfiguration")
            val hostField = klass.getDeclaredField("XMPP_SERVER_CHINA_HOST_P")
            hostField.isAccessible = true
            val host = hostField.get(null) as? String ?: return
            val method = klass.getDeclaredMethod("setXmppServerHost", String::class.java)
            method.isAccessible = true
            method.invoke(null, host)
        }
    }

    @Throws(Exception::class)
    private fun hookField(klass: Class<*>, field: String, value: Any) {
        val target: Field = klass.getDeclaredField(field)
        target.isAccessible = true
        target.set(null, value)
    }

    private fun hookFieldIfPresent(className: String, field: String, value: Any) {
        val klass = runCatching { Class.forName(className) }.getOrNull() ?: return
        hookField(klass, field, value)
    }
}
