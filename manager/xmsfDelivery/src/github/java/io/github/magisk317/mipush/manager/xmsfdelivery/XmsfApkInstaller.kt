package io.github.magisk317.mipush.manager.xmsfdelivery

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Terminal outcome of a [XmsfApkInstaller.install] attempt. */
sealed interface XmsfInstallEvent {
    /** The package installer reported STATUS_SUCCESS. */
    data object Success : XmsfInstallEvent

    data class Failed(val message: String) : XmsfInstallEvent
}

/**
 * Installs a verified runtime APK through [PackageInstaller] with the system
 * confirmation UI, modelled on the platform's own install flow.
 *
 * The status callback arrives on a runtime-registered broadcast receiver
 * (API 33+ requires RECEIVER_NOT_EXPORTED; the installer is the sender, but
 * the receiver is still not exported). [install] bridges the callback into a
 * coroutine and suspends until a *terminal* status: STATUS_PENDING_USER_ACTION
 * is not terminal — the session stays alive, so it is reported through
 * [onConfirmationRequired] and the caller must start that intent, after which
 * the real terminal status still arrives on the same receiver.
 */
class XmsfApkInstaller(private val context: Context) {

    private val appContext = context.applicationContext ?: context

    @Volatile
    private var activeSessionId: Int? = null

    @Volatile
    private var receiver: BroadcastReceiver? = null

    /** True when this app may call createSession without a settings detour. */
    val canInstall: Boolean
        get() = canRequestPackageInstalls(
            sdkInt = Build.VERSION.SDK_INT,
            canRequest = appContext.packageManager.canRequestPackageInstalls(),
        )

    /**
     * Install [apk]. Suspends until the installer reaches a terminal state
     * (success or failure). [onConfirmationRequired] fires when the system
     * wants the user to confirm; the caller must start the supplied intent.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun install(
        apk: File,
        onConfirmationRequired: (Intent) -> Unit = {},
    ): XmsfInstallEvent = suspendCancellableCoroutine { continuation ->
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // Defense in depth: the verified APK must declare the runtime package
        // the manager binds to, not whatever its manifest happens to say.
        params.setAppPackageName(ManagerProtocol.RUNTIME_PACKAGE)
        val sessionId = installer.createSession(params)
        activeSessionId = sessionId
        var session: PackageInstaller.Session? = null
        try {
            session = installer.openSession(sessionId)
            writeApk(session, apk)
            val statusReceiver = object : BroadcastReceiver() {
                override fun onReceive(receivedContext: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
                        ?: Int.MIN_VALUE
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        @Suppress("DEPRECATION")
                        val confirmation = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirmation != null) {
                            onConfirmationRequired(confirmation)
                        } else if (continuation.isActive) {
                            continuation.resume(
                                XmsfInstallEvent.Failed("installer confirmation intent missing"),
                            )
                        }
                        return
                    }
                    if (continuation.isActive) {
                        continuation.resume(intent.toTerminalEvent(status))
                    }
                }
            }
            receiver = statusReceiver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(statusReceiver, IntentFilter(ACTION_STATUS), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(statusReceiver, IntentFilter(ACTION_STATUS))
            }
            val statusIntent = Intent(ACTION_STATUS).setPackage(appContext.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val sender = PendingIntent.getBroadcast(appContext, sessionId, statusIntent, flags).intentSender
            session.commit(sender)
            // close() releases the local client object only; the system-side
            // session survives until it reaches a terminal status.
            runCatching { session.close() }
        } catch (error: Throwable) {
            runCatching { session?.close() }
            runCatching { installer.abandonSession(sessionId) }
            activeSessionId = null
            unregisterReceiver()
            if (continuation.isActive) {
                continuation.resume(XmsfInstallEvent.Failed(error.describeFailure()))
            }
        }
        continuation.invokeOnCancellation {
            runCatching { session?.abandon() }
            activeSessionId = null
            unregisterReceiver()
        }
    }

    /** Abandon any in-flight session and drop the status receiver. */
    fun cancel() {
        activeSessionId?.let { sessionId ->
            runCatching { appContext.packageManager.packageInstaller.abandonSession(sessionId) }
            activeSessionId = null
        }
        unregisterReceiver()
    }

    private fun writeApk(session: PackageInstaller.Session, apk: File) {
        val length = apk.length()
        var output: OutputStream? = null
        try {
            output = session.openWrite(APK_ENTRY_NAME, 0, length)
            apk.inputStream().use { input -> input.copyTo(output) }
            output.flush()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                session.fsync(output)
            }
        } finally {
            runCatching { output?.close() }
        }
    }

    private fun unregisterReceiver() {
        receiver?.let { current ->
            receiver = null
            runCatching { appContext.unregisterReceiver(current) }
        }
    }

    private fun Intent?.toTerminalEvent(status: Int): XmsfInstallEvent = when (status) {
        PackageInstaller.STATUS_SUCCESS -> XmsfInstallEvent.Success
        else -> {
            val message = this?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            XmsfInstallEvent.Failed(message ?: "installer failed with status $status")
        }
    }

    companion object {
        const val ACTION_STATUS = "io.github.magisk317.mipush.manager.xmsfdelivery.INSTALL_STATUS"
        const val APK_ENTRY_NAME = "xmsf-runtime.apk"

        /**
         * Pre-O the install-permission toggle does not exist, so an install
         * is always requestable; on O+ the user's per-app setting decides.
         */
        fun canRequestPackageInstalls(sdkInt: Int, canRequest: Boolean): Boolean =
            sdkInt < Build.VERSION_CODES.O || canRequest
    }
}
