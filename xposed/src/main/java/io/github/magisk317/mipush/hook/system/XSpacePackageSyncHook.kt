package io.github.magisk317.mipush.hook.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.UserHandle
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.hook.XLog
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object XSpacePackageSyncHook {
    private const val TAG = "XSpacePackageSyncHook"
    private const val XSPACE_USER_ID = 999
    private const val PACKAGE_STATE_TIMEOUT_SECONDS = 20L
    private const val PACKAGE_STATE_POLL_INTERVAL_MILLIS = 250L
    private const val PACKAGE_INSTALL_FLAGS = 0
    private const val DELETE_SYSTEM_APP_FLAG = 4
    private const val ACTION_SYNC_STATUS = "io.github.magisk317.mipush.action.XSPACE_SYNC_STATUS"
    private const val EXTRA_USER_HANDLE = "android.intent.extra.user_handle"

    private val installed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mipush-xspace-sync").apply { isDaemon = true }
    }

    private const val RETRY_DELAY_MS = 3000L

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handlePackageChange(context, intent)
            }
        }
        runCatching {
            registerReceiverForAllUsers(context, receiver, filter)
            XLog.i(TAG, "installed XSpace package sync receiver")
        }.onFailure { throwable ->
            installed.set(false)
            val isEarlyBootNpe = throwable is java.lang.reflect.InvocationTargetException &&
                throwable.cause is NullPointerException
            if (isEarlyBootNpe) {
                XLog.w(TAG, "receiver registration failed (early boot), scheduling retry")
                executor.execute {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        return@execute
                    }
                    install(context)
                }
            } else {
                XLog.e(TAG, "install XSpace package sync receiver failed", throwable)
            }
        }
    }

    internal fun handlePackageChange(context: Context, intent: Intent) {
        val packageName = intent.data?.encodedSchemeSpecificPart ?: return
        if (packageName != Constants.MANAGER_APP_NAME) return
        val userId = resolveUserId(intent)
        if (userId != XSPACE_USER_ID) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val action = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> installXmsfAction()
            Intent.ACTION_PACKAGE_REMOVED -> uninstallXmsfAction()
            else -> return
        }
        executor.execute { runPackageSync(context, action, intent.action.orEmpty()) }
    }

    internal fun resolveUserId(intent: Intent): Int? {
        val userHandle = intent.getIntExtra(EXTRA_USER_HANDLE, Int.MIN_VALUE)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        return resolveUserId(userHandle.takeUnless { it == Int.MIN_VALUE }, uid.takeIf { it >= 0 })
    }

    internal fun resolveUserId(userHandle: Int?, uid: Int?): Int? {
        if (userHandle != null) return userHandle
        return uid?.let { it / 100_000 }
    }

    internal fun installXmsfAction(): PackageSyncAction =
        PackageSyncAction.InstallExisting

    internal fun uninstallXmsfAction(): PackageSyncAction =
        PackageSyncAction.UninstallExisting

    internal enum class PackageSyncAction(
        val logName: String,
        val expectedInstalled: Boolean,
        val requestCode: Int,
    ) {
        InstallExisting("install-existing", expectedInstalled = true, requestCode = 0x9991),
        UninstallExisting("uninstall-existing", expectedInstalled = false, requestCode = 0x9992),
    }

    private fun registerReceiverForAllUsers(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        val allUsers = UserHandle::class.java.getField("ALL").get(null) as UserHandle
        Context::class.java
            .getMethod(
                "registerReceiverAsUser",
                BroadcastReceiver::class.java,
                UserHandle::class.java,
                IntentFilter::class.java,
                String::class.java,
                android.os.Handler::class.java,
            )
            .invoke(context, receiver, allUsers, filter, null, null)
    }

    private fun runPackageSync(context: Context, syncAction: PackageSyncAction, broadcastAction: String) {
        runCatching {
            val userContext = createContextForUser(context, XSPACE_USER_ID)
            val installer = userContext.packageManager.packageInstaller
            val sender = statusIntentSender(userContext, syncAction)

            when (syncAction) {
                PackageSyncAction.InstallExisting -> installer.installExistingPackage(
                    Constants.SERVICE_APP_NAME,
                    PACKAGE_INSTALL_FLAGS,
                    sender,
                )
                PackageSyncAction.UninstallExisting -> deletePackageForXSpaceUser()
            }

            if (waitForPackageState(userContext, syncAction.expectedInstalled)) {
                XLog.i(
                    TAG,
                    "XSpace sync package action succeeded broadcast=$broadcastAction action=${syncAction.logName} " +
                        "pkg=${Constants.SERVICE_APP_NAME} user=$XSPACE_USER_ID installed=${syncAction.expectedInstalled}",
                )
            } else {
                XLog.w(
                    TAG,
                    "XSpace sync package action timed out broadcast=$broadcastAction action=${syncAction.logName} " +
                        "pkg=${Constants.SERVICE_APP_NAME} user=$XSPACE_USER_ID expectedInstalled=${syncAction.expectedInstalled}",
                )
            }
        }.onFailure {
            XLog.e(
                TAG,
                "XSpace sync package action crashed broadcast=$broadcastAction action=${syncAction.logName} " +
                    "pkg=${Constants.SERVICE_APP_NAME} user=$XSPACE_USER_ID error=$it",
                it,
            )
        }
    }

    private fun createContextForUser(context: Context, userId: Int): Context {
        val userHandle = createUserHandle(userId)
        return Context::class.java
            .getMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType,
            )
            .invoke(context, userHandle, Context.CONTEXT_IGNORE_SECURITY) as Context
    }

    private fun createUserHandle(userId: Int): UserHandle {
        runCatching {
            return UserHandle::class.java
                .getMethod("of", Int::class.javaPrimitiveType)
                .invoke(null, userId) as UserHandle
        }
        val constructor = UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }
        return constructor.newInstance(userId)
    }

    private fun statusIntentSender(context: Context, action: PackageSyncAction): IntentSender {
        val intent = Intent(ACTION_SYNC_STATUS).apply {
            setPackage(context.packageName)
            putExtra("action", action.logName)
            putExtra("user", XSPACE_USER_ID)
            putExtra("package", Constants.SERVICE_APP_NAME)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, action.requestCode, intent, flags).intentSender
    }

    private fun deletePackageForXSpaceUser() {
        val packageManager = Class.forName("android.app.AppGlobals")
            .getMethod("getPackageManager")
            .invoke(null)
            ?: throw IllegalStateException("AppGlobals.getPackageManager returned null")
        val deleteMethod = packageManager.javaClass.methods.firstOrNull { method ->
            method.name == "deletePackageAsUser" &&
                method.parameterTypes.size == 5 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                method.parameterTypes[4] == Int::class.javaPrimitiveType
        } ?: throw NoSuchMethodException("deletePackageAsUser(String, int, observer, int, int)")
        deleteMethod.isAccessible = true
        deleteMethod.invoke(
            packageManager,
            Constants.SERVICE_APP_NAME,
            -1,
            null,
            XSPACE_USER_ID,
            DELETE_SYSTEM_APP_FLAG,
        )
    }

    private fun waitForPackageState(context: Context, installed: Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PACKAGE_STATE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (isPackageInstalled(context) == installed) return true
            Thread.sleep(PACKAGE_STATE_POLL_INTERVAL_MILLIS)
        }
        return isPackageInstalled(context) == installed
    }

    private fun isPackageInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(Constants.SERVICE_APP_NAME, 0)
            true
        }.getOrDefault(false)
    }
}
