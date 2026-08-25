package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.text.TextUtils
import io.github.magisk317.mipush.platform.support.Global
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.BuildConfig.DEBUG
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils.registeredApplicationDao
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Trumeet on 2017/12/23.
 */
object RegisteredApplicationDb {
    private val TAG = "RegisteredApplicationDb"
    private data class IslandSettingsKey(val userId: Int, val packageName: String)

    internal data class IslandSettings(
        val enabled: Boolean,
        val focusNotification: Boolean,
    )

    private val islandSettingsCache = ConcurrentHashMap<IslandSettingsKey, IslandSettings>()


    @JvmStatic
    fun registerApplication(pkg: String): RuntimeRegisteredApplicationRow {
        val startedAt = System.nanoTime()
        logD("registerApplication() called for: $pkg")
        val registeredApplication = getRegisteredApplication(pkg)
        val app = registeredApplication ?: create(pkg)
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "push",
                "stage" to "app_db_register",
                "reason" to if (registeredApplication != null) "existing" else "created",
                "target_package" to pkg,
            ),
            statusOk = true,
        )
        return app
    }

    @JvmStatic
    fun getRegisteredApplication(pkg: String): RuntimeRegisteredApplicationRow? {
        val list = getList(pkg)
        if (DEBUG) {
            logD("register -> existing list = $list")
        }
        return list.firstOrNull()
    }

    @JvmStatic
    private fun create(pkg: String): RuntimeRegisteredApplicationRow {
        val registeredApplication = RuntimeRegisteredApplicationRow(
            id = null,
            packageName = pkg,
            type = RegisteredAppType.ASK,
            notificationOnRegister = true,
            registeredType = RegisteredAppRegisteredType.NotRegistered,
            appName = Global.applicationNameCache()
                .getAppName(requireNotNull(Utils.getApplication()), pkg)
                .toString()
        )
        return registeredApplication.copy(id = insert(registeredApplication))
    }

    @JvmStatic
    fun getList(pkg: String?): List<RuntimeRegisteredApplicationRow> = runBlocking {
        if (TextUtils.isEmpty(pkg)) {
            registeredApplicationDao.getAll(currentUserId())
        } else {
            val item = registeredApplicationDao.getByPackageName(pkg!!, currentUserId())
            if (item == null) {
                emptyList()
            } else {
                listOf(item)
            }
        }
    }

    @JvmStatic
    fun update(application: RuntimeRegisteredApplicationRow): Long = runBlocking {
        val userId = currentUserId()
        // Never let a stale object id turn REPLACE into a cross-user delete.
        val existingId = registeredApplicationDao
            .getByPackageName(application.packageName, userId)
            ?.id
        val withId = application.copy(id = existingId, userId = userId)
        val id = registeredApplicationDao.insertOrReplace(withId)
        val finalId = if (withId.id == null || withId.id == 0L) id else (withId.id ?: id)
        islandSettingsCache[IslandSettingsKey(userId, application.packageName)] = withId.toIslandSettings()
        finalId
    }

    @JvmStatic
    private fun insert(application: RuntimeRegisteredApplicationRow): Long = runBlocking {
        val userId = currentUserId()
        val scoped = application.copy(userId = userId)
        registeredApplicationDao.insert(scoped).also {
            islandSettingsCache[IslandSettingsKey(userId, application.packageName)] = scoped.toIslandSettings()
        }
    }

    @JvmStatic
    fun updateBlocked(id: Long, blocked: Boolean): Int = runBlocking {
        registeredApplicationDao.updateBlocked(id, blocked, currentUserId())
    }

    @JvmStatic
    fun isBlocked(pkg: String): Boolean = runBlocking {
        registeredApplicationDao.isBlocked(pkg, currentUserId()) ?: false
    }

    @JvmStatic
    fun getIslandEnabled(pkg: String): Boolean? = runCatching {
        getIslandSettings(pkg)?.enabled
    }.getOrNull()

    @JvmStatic
    fun getIslandFocusNotificationEnabled(pkg: String): Boolean? = runCatching {
        getIslandSettings(pkg)?.focusNotification
    }.getOrNull()

    @JvmStatic
    internal fun getIslandSettings(pkg: String, requestedUserId: Int? = null): IslandSettings? {
        val userId = requestedUserId?.takeIf { it >= 0 } ?: currentUserId()
        val key = IslandSettingsKey(userId, pkg)
        return islandSettingsCache[key] ?: runBlocking {
            registeredApplicationDao.getByPackageName(pkg, userId)?.toIslandSettings()
        }?.also { islandSettingsCache[key] = it }
    }

    @JvmStatic
    fun markUnregistered(pkg: String, requestedUserId: Int? = null): Boolean = runBlocking {
        val userId = requestedUserId?.takeIf { it >= 0 } ?: currentUserId()
        val startedAt = System.nanoTime()
        val application = registeredApplicationDao.getByPackageName(pkg, userId)
        if (application == null) {
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "push",
                    "stage" to "app_db_unregister",
                    "reason" to "missing",
                    "target_package" to pkg,
                ),
                statusOk = true,
            )
            return@runBlocking false
        }
        if (application.registeredType == RegisteredAppRegisteredType.Unregistered) {
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "push",
                    "stage" to "app_db_unregister",
                    "reason" to "already_unregistered",
                    "target_package" to pkg,
                ),
                statusOk = true,
            )
            return@runBlocking false
        }
        val updated = application.copy(
            registeredType = RegisteredAppRegisteredType.Unregistered,
            userId = userId,
        )
        val ok = registeredApplicationDao.update(updated) > 0
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to if (ok) "ok" else "error",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "push",
                "stage" to "app_db_unregister",
                "reason" to if (ok) "updated" else "update_failed",
                "target_package" to pkg,
            ),
            statusOk = ok,
        )
        ok
    }

    private fun currentUserId(): Int = Utils.myUserId().coerceAtLeast(0)

    private fun RuntimeRegisteredApplicationRow.toIslandSettings(): IslandSettings = IslandSettings(
        enabled = islandEnabled,
        focusNotification = islandFocusNotification,
    )
}
