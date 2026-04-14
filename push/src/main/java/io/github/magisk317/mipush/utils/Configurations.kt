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
        // Bridging to modern initialization if needed
    }

    fun handle(packageName: String, container: XmPushActionContainer): Set<String> {
        // Bridging to modern decision logic. 
        // For now, returning empty set to stabilize build.
        // TODO: Map to actual configuration repository.
        return emptySet()
    }
}
