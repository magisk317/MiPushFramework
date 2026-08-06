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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import io.github.magisk317.xposed.logging.MagiskOtel

object XSpacePackageSyncHook {
    private const val USER_ID_DIVISOR = 100_000
    private const val TAG = "XSpacePackageSyncHook"
    private const val XSPACE_USER_ID = 999
    private const val PACKAGE_STATE_TIMEOUT_SECONDS = 20L
    private const val PACKAGE_STATE_POLL_INTERVAL_MILLIS = 250L
    private const val PACKAGE_INSTALL_FLAGS = 0
    private const val DELETE_SYSTEM_APP_FLAG = 4
    private const val ACTION_SYNC_STATUS = "io.github.magisk317.mipush.action.XSPACE_SYNC_STATUS"
    private const val EXTRA_USER_HANDLE = "android.intent.extra.user_handle"

    private val installed = AtomicBoolean(false)
    private val installGeneration = AtomicLong()
    private val installLock = Any()

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var registeredReceiver: BroadcastReceiver? = null

    @Volatile
    private var registeredContext: Context? = null

    private const val RETRY_DELAY_MS = 3000L

    fun install(context: Context) {
        install(context, expectedGeneration = null)
    }

    private fun install(context: Context, expectedGeneration: Long?) {
        val generation: Long
        synchronized(installLock) {
            if (expectedGeneration != null && installGeneration.get() != expectedGeneration) return
            if (installed.get()) return
            generation = installGeneration.incrementAndGet()
            installed.set(true)
            if (executor == null || executor!!.isShutdown) {
                executor = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "mipush-xspace-sync").apply { isDaemon = true }
                }
            }
        }
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
            val accepted = synchronized(installLock) {
                if (!acceptsRegistration(
                        expectedGeneration = generation,
                        currentGeneration = installGeneration.get(),
                        installed = installed.get(),
                    )
                ) {
                    false
                } else {
                    registeredContext = context
                    registeredReceiver = receiver
                    true
                }
            }
            if (!accepted) {
                // stop() may have completed while registerReceiverAsUser was in flight.
                // The old receiver was never published to stop(), so release it here.
                runCatching { context.unregisterReceiver(receiver) }
                return@runCatching
            }
            XLog.i(TAG, "installed XSpace package sync receiver")
            MagiskOtel.event(
                name = "push.xspace",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "install",
                ),
                statusOk = true,
            )
        }.onFailure { throwable ->
            val currentGeneration = synchronized(installLock) {
                if (installGeneration.get() != generation) {
                    false
                } else {
                    installed.set(false)
                    if (registeredReceiver === receiver) {
                        registeredReceiver = null
                        registeredContext = null
                    }
                    true
                }
            }
            // A reflective register call can fail after the framework has accepted the
            // receiver. The unregister is harmless when registration never completed.
            runCatching { context.unregisterReceiver(receiver) }
            if (!currentGeneration) return@onFailure
            val isEarlyBootNpe = throwable is java.lang.reflect.InvocationTargetException &&
                throwable.cause is NullPointerException
            if (isEarlyBootNpe) {
                XLog.w(TAG, "receiver registration failed (early boot), scheduling retry")
                val retryExecutor = synchronized(installLock) {
                    executor?.takeUnless { it.isShutdown }
                } ?: return@onFailure
                runCatching {
                    retryExecutor.execute {
                        try {
                            Thread.sleep(RETRY_DELAY_MS)
                        } catch (_: InterruptedException) {
                            return@execute
                        }
                        if (installGeneration.get() == generation) {
                            install(context, expectedGeneration = generation)
                        }
                    }
                }
            } else {
                XLog.e(TAG, "install XSpace package sync receiver failed", throwable)
            }
        }
    }

    /** Release receiver and executor owned by the old module ClassLoader before hot reload. */
    fun stop() {
        val receiver: BroadcastReceiver?
        val receiverContext: Context?
        val shutdownExecutor: ExecutorService?
        synchronized(installLock) {
            installGeneration.incrementAndGet()
            installed.set(false)
            receiver = registeredReceiver
            receiverContext = registeredContext
            shutdownExecutor = executor
            registeredReceiver = null
            registeredContext = null
            executor = null
        }
        if (receiverContext != null && receiver != null) {
            runCatching { receiverContext.unregisterReceiver(receiver) }
        }
        shutdownExecutor?.shutdownNow()
    }

    internal fun acceptsRegistration(
        expectedGeneration: Long,
        currentGeneration: Long,
        installed: Boolean,
    ): Boolean = expectedGeneration == currentGeneration && installed

    internal fun handlePackageChange(context: Context, intent: Intent) {
        val packageName = intent.data?.encodedSchemeSpecificPart ?: return
        if (packageName != Constants.MANAGER_APP_NAME) return
        val userId = resolveUserId(intent)
        if (userId != XSPACE_USER_ID) {
            emitXspace(
                result = "skip",
                stage = "package_change",
                reason = "non_xspace_user",
                targetPackage = packageName,
                packageAction = intent.action.orEmpty(),
            )
            return
        }
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            emitXspace(
                result = "skip",
                stage = "package_change",
                reason = "replacing",
                targetPackage = packageName,
                packageAction = intent.action.orEmpty(),
            )
            return
        }

        val action = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                // Never auto-install xmsf into user 999.
                // Dual-app enable is an explicit settings action (setDualAppEnabled).
                // Auto install-existing here resurrected zombie dual-space xmsf after
                // reboot/reinstall whenever manager was present in XSpace while the
                // dual_app_enabled preference stayed false.
                XLog.i(
                    TAG,
                    "ignore manager PACKAGE_ADDED in XSpace; dual-app install is explicit only",
                )
                emitXspace(
                    result = "skip",
                    stage = "package_change",
                    reason = "added_explicit_only",
                    targetPackage = packageName,
                    packageAction = intent.action.orEmpty(),
                )
                return
            }
            Intent.ACTION_PACKAGE_REMOVED -> uninstallXmsfAction()
            else -> {
                emitXspace(
                    result = "skip",
                    stage = "package_change",
                    reason = "unhandled_action",
                    targetPackage = packageName,
                    packageAction = intent.action.orEmpty(),
                )
                return
            }
        }
        emitXspace(
            result = "ok",
            stage = "package_change",
            reason = "scheduled",
            targetPackage = packageName,
            packageAction = action.name,
        )
        val syncExecutor = executor ?: return
        if (syncExecutor.isShutdown) return
        runCatching {
            syncExecutor.execute { runPackageSync(context, action, intent.action.orEmpty()) }
        }.onFailure {
            // stop() may shut the executor down between the state check and execute().
            XLog.w(TAG, "skip XSpace package sync because the executor is stopping")
        }
    }

    private fun emitXspace(
        result: String,
        stage: String,
        reason: String? = null,
        targetPackage: String? = null,
        packageAction: String? = null,
        statusOk: Boolean = true,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "hook",
            "stage" to stage,
        )
        if (reason != null) attrs["reason"] = reason
        if (!targetPackage.isNullOrBlank()) attrs["target_package"] = targetPackage
        if (!packageAction.isNullOrBlank()) attrs["package_action"] = packageAction
        MagiskOtel.event(name = "push.xspace", attributes = attrs, statusOk = statusOk)
    }

    internal fun resolveUserId(intent: Intent): Int? {
        val userHandle = intent.getIntExtra(EXTRA_USER_HANDLE, Int.MIN_VALUE)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        return resolveUserId(userHandle.takeUnless { it == Int.MIN_VALUE }, uid.takeIf { it >= 0 })
    }

    internal fun resolveUserId(userHandle: Int?, uid: Int?): Int? {
        if (userHandle != null) return userHandle
        return uid?.let { it / USER_ID_DIVISOR }
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
