package android.app

import android.annotation.TargetApi
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.IBinder

open class AppOpsManager {
    companion object {
        const val MODE_ALLOWED = 0
        const val MODE_IGNORED = 1
        const val MODE_ERRORED = 2
        const val MODE_DEFAULT = 3
        const val MODE_FOREGROUND = 4

        @TargetApi(Build.VERSION_CODES.N)
        const val OP_RUN_IN_BACKGROUND = 63
        const val OP_POST_NOTIFICATION = 11
        const val OP_GET_USAGE_STATS = 43

        const val OPSTR_GET_USAGE_STATS = "android:get_usage_stats"
        const val OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window"

        @JvmStatic
        fun permissionToOp(permission: String): String {
            throw RuntimeException("Stub!")
        }
    }

    open fun startWatchingMode(op: String, packageName: String?, callback: OnOpChangedListener) {
        throw RuntimeException("Stub!")
    }

    open fun stopWatchingMode(callback: OnOpChangedListener) {
        throw RuntimeException("Stub!")
    }

    open fun checkOp(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun checkOpNoThrow(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun checkOp(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun checkOpNoThrow(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteOp(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteOpNoThrow(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteProxyOp(op: String, proxiedPackageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteProxyOpNoThrow(op: String, proxiedPackageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOp(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOpNoThrow(op: String, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun finishOp(op: String, uid: Int, packageName: String) {
        throw RuntimeException("Stub!")
    }

    open fun setUidMode(code: Int, uid: Int, mode: Int) {
        throw RuntimeException("Stub!")
    }

    open fun setUidMode(appOp: String, uid: Int, mode: Int) {
        throw RuntimeException("Stub!")
    }

    open fun setUserRestriction(code: Int, restricted: Boolean, token: IBinder) {
        throw RuntimeException("Stub!")
    }

    open fun setUserRestriction(code: Int, restricted: Boolean, token: IBinder, exceptionPackages: Array<String>?) {
        throw RuntimeException("Stub!")
    }

    open fun setUserRestrictionForUser(
        code: Int,
        restricted: Boolean,
        token: IBinder,
        exceptionPackages: Array<String>?,
        userId: Int
    ) {
        throw RuntimeException("Stub!")
    }

    open fun setMode(code: Int, uid: Int, packageName: String, mode: Int) {
        throw RuntimeException("Stub!")
    }

    open fun setRestriction(code: Int, usage: Int, mode: Int, exceptionPackages: Array<String>?) {
        throw RuntimeException("Stub!")
    }

    open fun resetAllModes() {
        throw RuntimeException("Stub!")
    }

    open fun startWatchingMode(op: Int, packageName: String?, callback: OnOpChangedListener) {
        throw RuntimeException("Stub!")
    }

    open fun noteOp(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteProxyOp(op: Int, proxiedPackageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteProxyOpNoThrow(op: Int, proxiedPackageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteOpNoThrow(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteOp(op: Int): Int {
        throw RuntimeException("Stub!")
    }

    open fun noteOpNoThrow(op: Int): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOp(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOpNoThrow(op: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOp(op: Int): Int {
        throw RuntimeException("Stub!")
    }

    open fun startOpNoThrow(op: Int): Int {
        throw RuntimeException("Stub!")
    }

    open fun finishOp(op: Int, uid: Int, packageName: String) {
        throw RuntimeException("Stub!")
    }

    open fun finishOp(op: Int) {
        throw RuntimeException("Stub!")
    }

    open fun checkPackage(uid: Int, packageName: String) {
        throw RuntimeException("Stub!")
    }

    open fun checkAudioOp(op: Int, stream: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    open fun checkAudioOpNoThrow(op: Int, stream: Int, uid: Int, packageName: String): Int {
        throw RuntimeException("Stub!")
    }

    interface OnOpChangedListener {
        fun onOpChanged(op: String, packageName: String)
    }
}
