package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.BuildConfig.DEBUG
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils.registeredApplicationDao
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

/**
 * Created by Trumeet on 2017/12/23.
 */
object RegisteredApplicationDb {
    private val TAG = "RegisteredApplicationDb"


    @JvmStatic
    fun registerApplication(pkg: String): RegisteredApplication {
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
    fun getRegisteredApplication(pkg: String): RegisteredApplication? {
        val list = getList(pkg)
        if (DEBUG) {
            logD("register -> existing list = $list")
        }
        return list.firstOrNull()
    }

    @JvmStatic
    private fun create(pkg: String): RegisteredApplication {
        val registeredApplication = RegisteredApplication(
            id = null,
            packageName = pkg,
            type = RegisteredApplication.Type.ASK,
            notificationOnRegister = true,
            registeredType = RegisteredApplication.RegisteredType.NotRegistered,
            appName = Global.applicationNameCache()
                .getAppName(requireNotNull(Utils.getApplication()), pkg)
                .toString()
        )
        registeredApplication.id = insert(registeredApplication)
        return registeredApplication
    }

    @JvmStatic
    fun getList(pkg: String?): List<RegisteredApplication> = runBlocking {
        if (TextUtils.isEmpty(pkg)) {
            registeredApplicationDao.getAll()
        } else {
            val item = registeredApplicationDao.getByPackageName(pkg!!)
            if (item == null) {
                emptyList()
            } else {
                listOf(item)
            }
        }
    }

    @JvmStatic
    fun update(application: RegisteredApplication): Long = runBlocking {
        val id = registeredApplicationDao.insertOrReplace(application)
        application.id = if (application.id == null || application.id == 0L) id else application.id
        application.id ?: id
    }

    @JvmStatic
    private fun insert(application: RegisteredApplication): Long = runBlocking {
        registeredApplicationDao.insert(application)
    }

    @JvmStatic
    fun updateBlocked(id: Long, blocked: Boolean): Int = runBlocking {
        registeredApplicationDao.updateBlocked(id, blocked)
    }

    @JvmStatic
    fun isBlocked(pkg: String): Boolean = runBlocking {
        registeredApplicationDao.isBlocked(pkg) ?: false
    }

    @JvmStatic
    fun getIslandEnabled(pkg: String): Boolean? = runCatching {
        runBlocking {
            registeredApplicationDao.isIslandEnabled(pkg)
        }
    }.getOrNull()

    @JvmStatic
    fun getIslandFocusNotificationEnabled(pkg: String): Boolean? = runCatching {
        runBlocking {
            registeredApplicationDao.isIslandFocusNotificationEnabled(pkg)
        }
    }.getOrNull()

    @JvmStatic
    fun markUnregistered(pkg: String): Boolean = runBlocking {
        val startedAt = System.nanoTime()
        val application = registeredApplicationDao.getByPackageName(pkg)
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
        if (application.registeredType == RegisteredApplication.RegisteredType.Unregistered) {
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
        application.registeredType = RegisteredApplication.RegisteredType.Unregistered
        val ok = registeredApplicationDao.update(application) > 0
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
}
