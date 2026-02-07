package android.app

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.IBinder

@TargetApi(Build.VERSION_CODES.KITKAT)
open class AppOpsManagerExtender(context: Context) : AppOpsManager() {
    private val delegate: AppOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    override fun setUidMode(code: Int, uid: Int, mode: Int) = delegate.setUidMode(code, uid, mode)
    override fun setUidMode(appOp: String, uid: Int, mode: Int) = delegate.setUidMode(appOp, uid, mode)
    override fun setUserRestriction(code: Int, restricted: Boolean, token: IBinder) = delegate.setUserRestriction(code, restricted, token)
    override fun setUserRestriction(code: Int, restricted: Boolean, token: IBinder, exceptionPackages: Array<String>?) =
        delegate.setUserRestriction(code, restricted, token, exceptionPackages)

    override fun setUserRestrictionForUser(
        code: Int,
        restricted: Boolean,
        token: IBinder,
        exceptionPackages: Array<String>?,
        userId: Int
    ) = delegate.setUserRestrictionForUser(code, restricted, token, exceptionPackages, userId)

    override fun setMode(code: Int, uid: Int, packageName: String, mode: Int) = delegate.setMode(code, uid, packageName, mode)
    override fun setRestriction(code: Int, usage: Int, mode: Int, exceptionPackages: Array<String>?) =
        delegate.setRestriction(code, usage, mode, exceptionPackages)

    override fun resetAllModes() = delegate.resetAllModes()

    override fun startWatchingMode(op: String, packageName: String?, callback: OnOpChangedListener) =
        delegate.startWatchingMode(op, packageName, callback)

    override fun startWatchingMode(op: Int, packageName: String?, callback: OnOpChangedListener) =
        delegate.startWatchingMode(op, packageName, callback)

    override fun stopWatchingMode(callback: OnOpChangedListener) = delegate.stopWatchingMode(callback)

    override fun checkOp(op: String, uid: Int, packageName: String): Int = delegate.checkOp(op, uid, packageName)
    override fun checkOpNoThrow(op: String, uid: Int, packageName: String): Int = delegate.checkOpNoThrow(op, uid, packageName)
    override fun noteOp(op: String, uid: Int, packageName: String): Int = delegate.noteOp(op, uid, packageName)
    override fun noteOpNoThrow(op: String, uid: Int, packageName: String): Int = delegate.noteOpNoThrow(op, uid, packageName)

    @TargetApi(Build.VERSION_CODES.M)
    override fun noteProxyOp(op: String, proxiedPackageName: String): Int = delegate.noteProxyOp(op, proxiedPackageName)

    @TargetApi(Build.VERSION_CODES.M)
    override fun noteProxyOpNoThrow(op: String, proxiedPackageName: String): Int = delegate.noteProxyOpNoThrow(op, proxiedPackageName)

    override fun startOp(op: String, uid: Int, packageName: String): Int = delegate.startOp(op, uid, packageName)
    override fun startOpNoThrow(op: String, uid: Int, packageName: String): Int = delegate.startOpNoThrow(op, uid, packageName)
    override fun finishOp(op: String, uid: Int, packageName: String) = delegate.finishOp(op, uid, packageName)

    override fun checkOp(op: Int, uid: Int, packageName: String): Int = delegate.checkOp(op, uid, packageName)
    override fun checkOpNoThrow(op: Int, uid: Int, packageName: String): Int = delegate.checkOpNoThrow(op, uid, packageName)
    override fun checkPackage(uid: Int, packageName: String) = delegate.checkPackage(uid, packageName)
    override fun checkAudioOp(op: Int, stream: Int, uid: Int, packageName: String): Int =
        delegate.checkAudioOp(op, stream, uid, packageName)

    override fun checkAudioOpNoThrow(op: Int, stream: Int, uid: Int, packageName: String): Int =
        delegate.checkAudioOpNoThrow(op, stream, uid, packageName)

    override fun noteOp(op: Int, uid: Int, packageName: String): Int = delegate.noteOp(op, uid, packageName)
    override fun noteProxyOp(op: Int, proxiedPackageName: String): Int = delegate.noteProxyOp(op, proxiedPackageName)
    override fun noteProxyOpNoThrow(op: Int, proxiedPackageName: String): Int = delegate.noteProxyOpNoThrow(op, proxiedPackageName)
    override fun noteOpNoThrow(op: Int, uid: Int, packageName: String): Int = delegate.noteOpNoThrow(op, uid, packageName)
    override fun noteOp(op: Int): Int = delegate.noteOp(op)
    override fun noteOpNoThrow(op: Int): Int = delegate.noteOpNoThrow(op)

    override fun startOp(op: Int, uid: Int, packageName: String): Int = delegate.startOp(op, uid, packageName)
    override fun startOpNoThrow(op: Int, uid: Int, packageName: String): Int = delegate.startOpNoThrow(op, uid, packageName)
    override fun startOp(op: Int): Int = delegate.startOp(op)
    override fun startOpNoThrow(op: Int): Int = delegate.startOpNoThrow(op)
    override fun finishOp(op: Int, uid: Int, packageName: String) = delegate.finishOp(op, uid, packageName)
    override fun finishOp(op: Int) = delegate.finishOp(op)
}
