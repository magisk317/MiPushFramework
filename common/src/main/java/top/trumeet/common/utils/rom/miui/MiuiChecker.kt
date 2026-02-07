package top.trumeet.common.utils.rom.miui

import android.app.Application
import android.util.Log
import top.trumeet.common.utils.Utils
import top.trumeet.common.utils.rom.RomChecker

/**
 * Created by Trumeet on 2018/4/22.
 */
class MiuiChecker : RomChecker {
    companion object {
        private const val TAG = "MiuiChecker"
    }

    private fun checkSdkBasic(): Boolean {
        return try {
            if (MiuiFileUtils.isMiuiSystem() || MiuiDexUtils.load(
                    MiuiFileUtils.getApkPath(null, "com.miui.core", "miui"),
                    null,
                    MiuiFileUtils.getLibPath(null, "com.miui.core"),
                    Application::class.java.classLoader!!
                )
            ) {
                true
            } else {
                // No sdk
                false
            }
        } catch (th: Throwable) {
            Log.e(TAG, th.message ?: "Unknown error in checkSdkBasic")
            false
        }
    }

    private fun initializeSdk(): Boolean {
        return try {
            val app = Utils.getApplication() as? Application
            val hashMap = HashMap<String, Any>()
            val method = MiuiSdkManagerHelper.getSdkManagerClass()
                .getMethod("initialize", Application::class.java, Map::class.java)
            val intValue = method.invoke(null, app, hashMap) as Int
            if (intValue == 0) {
                true
            } else {
                Log.d(TAG, "initialize: $intValue")
                false
            }
        } catch (th: Throwable) {
            Log.e(TAG, "initializeSdk: ${th.message}")
            false
        }
    }

    private fun startSdk(): Boolean {
        return try {
            val hashMap = HashMap<String, Any>()
            val method = MiuiSdkManagerHelper.getSdkManagerClass()
                .getMethod("start", Map::class.java)
            val intValue = method.invoke(null, hashMap) as Int
            when (intValue) {
                1 -> false // Low sdk version
                0 -> true
                else -> {
                    Log.e(TAG, "start: $intValue")
                    false
                }
            }
        } catch (th: Throwable) {
            Log.e(TAG, "startSdk: ${th.message}")
            false
        }
    }

    override fun check(): Boolean {
        return checkSdkBasic() && initializeSdk() && startSdk()
    }
}
