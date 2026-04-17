package io.github.magisk317.mipush.runtime.store.db

import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.BuildConfig.DEBUG
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils.registeredApplicationDao
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

/**
 * Created by Trumeet on 2017/12/23.
 */
object RegisteredApplicationDb {
    private val TAG = "RegisteredApplicationDb"
    private val logger = object {
        fun i(msg: String) = Napier.i(msg, tag = TAG)
        fun d(msg: String) = Napier.d(msg, tag = TAG)
    }


    @JvmStatic
    fun registerApplication(pkg: String): RegisteredApplication {
        logger.i("registerApplication() called for: $pkg")
        val registeredApplication = getRegisteredApplication(pkg)
        return registeredApplication ?: create(pkg)
    }

    @JvmStatic
    fun getRegisteredApplication(pkg: String): RegisteredApplication? {
        val list = getList(pkg)
        if (DEBUG) {
            logger.d("register -> existing list = $list")
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
}
