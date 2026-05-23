package io.github.magisk317.mipush.hook.compat.legacyhuawei

import android.content.pm.PackageInfo
import android.os.Build
import android.util.Base64
import dalvik.system.DexClassLoader
import io.github.magisk317.mipush.common.LEGACY_HUAWEI_CORE_SIGNATURE
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.*

object LegacyHuaweiSignatureCompat {
    private const val TAG = "LegacyHuaweiSignatureCompat"

    private var verifyApkHashHooked = false
    private var verifyApkHashUnhook: HookHandle? = null

    fun hook(lpparam: LoadParam) {
        XLog.d(TAG, "hook() called with: processName = ${lpparam.processName}")

        tryHookVerifyApkHash(lpparam.classLoader)

        if (!verifyApkHashHooked) {
            verifyApkHashUnhook = DexClassLoader::class.java.hookConstructor(String::class.java, String::class.java, String::class.java, ClassLoader::class.java) {
                doAfter { tryHookVerifyApkHash(thisObject as ClassLoader) }
            }
        }

        val classApplicationPackageManager = lpparam.classLoader.findClass("android.app.ApplicationPackageManager")
        classApplicationPackageManager.hookMethod("getPackageInfo", String::class.java, Int::class.java) {
            doAfter {
                val packageName = args.getOrNull(0) as? String ?: return@doAfter
                if (packageName != XMSF_PACKAGE_NAME) return@doAfter

                val info = result as? PackageInfo ?: return@doAfter
                val fakeSignatureBytes = Base64.decode(LEGACY_HUAWEI_CORE_SIGNATURE, Base64.NO_WRAP)

                @Suppress("DEPRECATION")
                val signatures = info.signatures
                val firstSignature = signatures?.firstOrNull()
                if (firstSignature != null) {
                    XposedHelpers.setObjectField(firstSignature, "mSignature", fakeSignatureBytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signingInfo = info.signingInfo
                    if (signingInfo != null) {
                        runCatching {
                            val apkSigners = signingInfo.apkContentsSigners
                            val signer = apkSigners?.firstOrNull()
                            if (signer != null) {
                                XposedHelpers.setObjectField(signer, "mSignature", fakeSignatureBytes)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tryHookVerifyApkHash(classLoader: ClassLoader) {
        if (verifyApkHashHooked) return

        try {
            classLoader.findClass("com.huawei.hms.utils.ReadApkFileUtil")
                .hookMethod("verifyApkHash", String::class.java) { replace { true } }

            XLog.d(TAG, "tryHookVerifyApkHash: verifyApkHash() hooked")

            verifyApkHashHooked = true
            verifyApkHashUnhook?.unhook()
        } catch (e: XposedHelpers.ClassNotFoundError) {
            XLog.d(TAG, "tryHookVerifyApkHash: ClassNotFoundError")
        } catch (e: NoSuchMethodError) {
            XLog.d(TAG, "tryHookVerifyApkHash: NoSuchMethodError")
        } catch (e: Throwable) {
            //ignore
        }
    }
}
