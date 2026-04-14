package io.github.magisk317.mipush.utils

import android.content.Context
import android.net.Uri
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compatibility wrapper for legacy Configurations singleton.
 * Bridging modern configuration logic to legacy call sites.
 */
@Singleton
class Configurations @Inject constructor() {
    companion object {
        private var instance: Configurations? = null

        @JvmStatic
        fun getInstance(): Configurations {
            if (instance == null) {
                instance = Configurations()
            }
            return instance!!
        }
    }

    fun init(context: Context, directory: Uri?) {
        // Delegate to the legacy Configurations which owns the actual loader.
        com.xiaomi.xmsf.push.utils.Configurations.getInstance().init(context, directory)
    }

    fun handle(packageName: String, container: XmPushActionContainer): Set<String> {
        // Delegate to the legacy Configurations which owns the actual config matching logic.
        return try {
            com.xiaomi.xmsf.push.utils.Configurations.getInstance().handle(packageName, container)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.e(
                "Configurations.handle failed for $packageName",
                t,
                tag = "Configurations"
            )
            emptySet()
        }
    }
}
